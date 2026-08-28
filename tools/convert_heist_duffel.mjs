import fs from "node:fs";
import path from "node:path";

const sourcePath = path.resolve(process.argv[2] ?? "C:/Users/famil/Downloads/military_duffel_bag.glb");
const outputRoot = path.resolve(process.argv[3] ?? "build/generated/heist-duffel");
const source = fs.readFileSync(sourcePath);
if (source.toString("ascii", 0, 4) !== "glTF") throw new Error("Expected binary glTF input.");

const jsonLength = source.readUInt32LE(12);
const json = JSON.parse(source.subarray(20, 20 + jsonLength).toString("utf8"));
const binaryOffset = 20 + jsonLength + 8;
const identity = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];

function multiply(left, right) {
    const out = Array(16).fill(0);
    for (let column = 0; column < 4; column++) for (let row = 0; row < 4; row++) {
        for (let index = 0; index < 4; index++) out[column * 4 + row] += left[index * 4 + row] * right[column * 4 + index];
    }
    return out;
}

function nodeMatrix(node) {
    if (node.matrix) return node.matrix;
    const [tx, ty, tz] = node.translation ?? [0, 0, 0];
    const [x, y, z, w] = node.rotation ?? [0, 0, 0, 1];
    const [sx, sy, sz] = node.scale ?? [1, 1, 1];
    return [
        (1 - 2*y*y - 2*z*z)*sx, (2*x*y + 2*z*w)*sx, (2*x*z - 2*y*w)*sx, 0,
        (2*x*y - 2*z*w)*sy, (1 - 2*x*x - 2*z*z)*sy, (2*y*z + 2*x*w)*sy, 0,
        (2*x*z + 2*y*w)*sz, (2*y*z - 2*x*w)*sz, (1 - 2*x*x - 2*y*y)*sz, 0,
        tx, ty, tz, 1
    ];
}

function point(matrix, value) {
    return [
        matrix[0]*value[0] + matrix[4]*value[1] + matrix[8]*value[2] + matrix[12],
        matrix[1]*value[0] + matrix[5]*value[1] + matrix[9]*value[2] + matrix[13],
        matrix[2]*value[0] + matrix[6]*value[1] + matrix[10]*value[2] + matrix[14]
    ];
}

function normal(matrix, value) {
    const transformed = point([...matrix.slice(0, 12), 0, 0, 0, 1], value);
    const length = Math.hypot(...transformed) || 1;
    return transformed.map(component => component / length);
}

function accessor(index) {
    const item = json.accessors[index];
    const view = json.bufferViews[item.bufferView];
    const counts = {SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4};
    const sizes = {5121: 1, 5123: 2, 5125: 4, 5126: 4};
    const count = counts[item.type];
    const size = sizes[item.componentType];
    const stride = view.byteStride ?? count * size;
    const start = binaryOffset + (view.byteOffset ?? 0) + (item.byteOffset ?? 0);
    const values = [];
    for (let i = 0; i < item.count; i++) {
        const entry = [];
        for (let c = 0; c < count; c++) {
            const offset = start + i * stride + c * size;
            entry.push(item.componentType === 5121 ? source.readUInt8(offset)
                : item.componentType === 5123 ? source.readUInt16LE(offset)
                : item.componentType === 5125 ? source.readUInt32LE(offset)
                : source.readFloatLE(offset));
        }
        values.push(count === 1 ? entry[0] : entry);
    }
    return values;
}

const meshNode = json.nodes.findIndex(node => node.mesh === 0);
const parent = new Map();
json.nodes.forEach((node, parentIndex) => (node.children ?? []).forEach(child => parent.set(child, parentIndex)));
const hierarchy = [];
for (let index = meshNode; index !== undefined; index = parent.get(index)) hierarchy.unshift(index);
const world = hierarchy.reduce((matrix, index) => multiply(matrix, nodeMatrix(json.nodes[index])), identity);
const primitive = json.meshes[0].primitives[0];
const sourcePositions = accessor(primitive.attributes.POSITION).map(value => point(world, value));
const sourceNormals = accessor(primitive.attributes.NORMAL).map(value => normal(world, value));
const uvs = accessor(primitive.attributes.TEXCOORD_0);
const indices = accessor(primitive.indices);
const min = [Infinity, Infinity, Infinity];
const max = [-Infinity, -Infinity, -Infinity];
sourcePositions.forEach(value => value.forEach((component, axis) => {
    min[axis] = Math.min(min[axis], component); max[axis] = Math.max(max[axis], component);
}));
const size = max.map((component, axis) => component - min[axis]);
const center = max.map((component, axis) => (component + min[axis]) / 2);

// Lay the source's long X axis across Minecraft X while retaining a practical block footprint.
const positions = sourcePositions.map(value => [
    0.5 + (value[0] - center[0]) / size[0] * 0.90,
    0.06 + (value[1] - min[1]) / size[1] * 0.46,
    0.5 + (value[2] - center[2]) / size[2] * 0.66
]);
const normals = sourceNormals.map(value => {
    const mapped = [value[0], value[1], value[2]];
    const length = Math.hypot(...mapped) || 1;
    return mapped.map(component => component / length);
});

fs.mkdirSync(outputRoot, {recursive: true});
const lines = [
    "# Military Duffel bag by Sousinho, CC BY 4.0",
    "# https://sketchfab.com/3d-models/military-duffel-bag-d69478f0c5334e189e98f99e84bbe3e6",
    "mtllib heist_duffel.mtl", "o Heist_Duffel", "usemtl heist_duffel"
];
positions.forEach(([x, y, z]) => lines.push(`v ${x} ${y} ${z}`));
uvs.forEach(([u, v]) => lines.push(`vt ${u} ${v}`));
normals.forEach(([x, y, z]) => lines.push(`vn ${x} ${y} ${z}`));
for (let i = 0; i < indices.length; i += 3) {
    const a = indices[i] + 1, b = indices[i + 1] + 1, c = indices[i + 2] + 1;
    lines.push(`f ${a}/${a}/${a} ${b}/${b}/${b} ${c}/${c}/${c}`);
}
fs.writeFileSync(path.join(outputRoot, "heist_duffel.obj"), `${lines.join("\n")}\n`);
fs.writeFileSync(path.join(outputRoot, "heist_duffel.mtl"), [
    "newmtl heist_duffel", "Ka 1.0 1.0 1.0", "Kd 1.0 1.0 1.0", "Ks 0.0 0.0 0.0",
    "d 1.0", "illum 2", "map_Kd ultimatebankingsystem:block/heist_duffel", ""
].join("\n"));
const image = json.images[json.textures[0].source];
const imageView = json.bufferViews[image.bufferView];
const imageStart = binaryOffset + (imageView.byteOffset ?? 0);
fs.writeFileSync(path.join(outputRoot, "heist_duffel.jpg"), source.subarray(imageStart, imageStart + imageView.byteLength));
console.log(JSON.stringify({hierarchy, vertices: positions.length, triangles: indices.length / 3, bounds: {min, max, size}}, null, 2));
