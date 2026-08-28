import fs from "node:fs";
import path from "node:path";

const sourcePath = path.resolve(
    process.argv[2] ?? "C:/Users/famil/Downloads/dallas_mask__payday_2.glb"
);
const outputRoot = path.resolve(process.argv[3] ?? "build/generated/dallas-mask");

const source = fs.readFileSync(sourcePath);
if (source.toString("ascii", 0, 4) !== "glTF") {
    throw new Error("Expected a binary glTF file.");
}

const jsonLength = source.readUInt32LE(12);
const json = JSON.parse(source.subarray(20, 20 + jsonLength).toString("utf8"));
const binaryChunkHeader = 20 + jsonLength;
const binaryOffset = binaryChunkHeader + 8;

const identity = [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1
];

function multiply(left, right) {
    const result = Array(16).fill(0);
    for (let column = 0; column < 4; column++) {
        for (let row = 0; row < 4; row++) {
            for (let index = 0; index < 4; index++) {
                result[column * 4 + row] += left[index * 4 + row] * right[column * 4 + index];
            }
        }
    }
    return result;
}

function nodeMatrix(node) {
    if (node.matrix) {
        return node.matrix;
    }
    const translation = node.translation ?? [0, 0, 0];
    const rotation = node.rotation ?? [0, 0, 0, 1];
    const scale = node.scale ?? [1, 1, 1];
    const [x, y, z, w] = rotation;
    const rotationMatrix = [
        1 - 2 * y * y - 2 * z * z, 2 * x * y + 2 * z * w, 2 * x * z - 2 * y * w, 0,
        2 * x * y - 2 * z * w, 1 - 2 * x * x - 2 * z * z, 2 * y * z + 2 * x * w, 0,
        2 * x * z + 2 * y * w, 2 * y * z - 2 * x * w, 1 - 2 * x * x - 2 * y * y, 0,
        translation[0], translation[1], translation[2], 1
    ];
    rotationMatrix[0] *= scale[0];
    rotationMatrix[1] *= scale[0];
    rotationMatrix[2] *= scale[0];
    rotationMatrix[4] *= scale[1];
    rotationMatrix[5] *= scale[1];
    rotationMatrix[6] *= scale[1];
    rotationMatrix[8] *= scale[2];
    rotationMatrix[9] *= scale[2];
    rotationMatrix[10] *= scale[2];
    return rotationMatrix;
}

function transformPoint(matrix, point) {
    return [
        matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12],
        matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13],
        matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14]
    ];
}

function transformNormal(matrix, normal) {
    const transformed = [
        matrix[0] * normal[0] + matrix[4] * normal[1] + matrix[8] * normal[2],
        matrix[1] * normal[0] + matrix[5] * normal[1] + matrix[9] * normal[2],
        matrix[2] * normal[0] + matrix[6] * normal[1] + matrix[10] * normal[2]
    ];
    const length = Math.hypot(...transformed) || 1;
    return transformed.map(value => value / length);
}

function readAccessor(accessorIndex) {
    const accessor = json.accessors[accessorIndex];
    const view = json.bufferViews[accessor.bufferView];
    const componentCounts = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4 };
    const componentSizes = { 5121: 1, 5123: 2, 5125: 4, 5126: 4 };
    const componentCount = componentCounts[accessor.type];
    const componentSize = componentSizes[accessor.componentType];
    const stride = view.byteStride ?? componentCount * componentSize;
    const start = binaryOffset + (view.byteOffset ?? 0) + (accessor.byteOffset ?? 0);
    const values = [];
    for (let index = 0; index < accessor.count; index++) {
        const entry = [];
        const offset = start + index * stride;
        for (let component = 0; component < componentCount; component++) {
            const componentOffset = offset + component * componentSize;
            switch (accessor.componentType) {
                case 5121: entry.push(source.readUInt8(componentOffset)); break;
                case 5123: entry.push(source.readUInt16LE(componentOffset)); break;
                case 5125: entry.push(source.readUInt32LE(componentOffset)); break;
                case 5126: entry.push(source.readFloatLE(componentOffset)); break;
                default: throw new Error(`Unsupported component type ${accessor.componentType}`);
            }
        }
        values.push(componentCount === 1 ? entry[0] : entry);
    }
    return values;
}

let meshNodeIndex = -1;
for (let index = 0; index < json.nodes.length; index++) {
    if (json.nodes[index].mesh === 0) {
        meshNodeIndex = index;
        break;
    }
}
if (meshNodeIndex < 0) {
    throw new Error("Dallas mask mesh node was not found.");
}

const parentByChild = new Map();
json.nodes.forEach((node, parentIndex) => {
    for (const child of node.children ?? []) {
        parentByChild.set(child, parentIndex);
    }
});
const hierarchy = [];
for (let index = meshNodeIndex; index !== undefined; index = parentByChild.get(index)) {
    hierarchy.unshift(index);
}
const worldMatrix = hierarchy.reduce(
    (matrix, index) => multiply(matrix, nodeMatrix(json.nodes[index])),
    identity
);

const primitive = json.meshes[0].primitives[0];
const positions = readAccessor(primitive.attributes.POSITION).map(value => transformPoint(worldMatrix, value));
const normals = readAccessor(primitive.attributes.NORMAL).map(value => transformNormal(worldMatrix, value));
const uvs = readAccessor(primitive.attributes.TEXCOORD_0);
const indices = readAccessor(primitive.indices);

const minimum = [Infinity, Infinity, Infinity];
const maximum = [-Infinity, -Infinity, -Infinity];
for (const position of positions) {
    for (let axis = 0; axis < 3; axis++) {
        minimum[axis] = Math.min(minimum[axis], position[axis]);
        maximum[axis] = Math.max(maximum[axis], position[axis]);
    }
}
const size = maximum.map((value, axis) => value - minimum[axis]);
const center = maximum.map((value, axis) => (value + minimum[axis]) / 2);
const normalized = positions.map(position => [
    0.5 + (position[0] - center[0]) / size[1],
    (position[1] - minimum[1]) / size[1],
    0.5 + (position[2] - center[2]) / size[1]
]);

fs.mkdirSync(outputRoot, { recursive: true });
const lines = [
    "# Converted from Dallas mask | Payday 2 by SirDJCat (CC BY-NC 4.0)",
    "mtllib dallas_mask.mtl",
    "o Dallas_Mask",
    "usemtl dallas_mask"
];
for (const [x, y, z] of normalized) lines.push(`v ${x} ${y} ${z}`);
for (const [u, v] of uvs) lines.push(`vt ${u} ${v}`);
for (const [x, y, z] of normals) lines.push(`vn ${x} ${y} ${z}`);
for (let index = 0; index < indices.length; index += 3) {
    const a = indices[index] + 1;
    const b = indices[index + 1] + 1;
    const c = indices[index + 2] + 1;
    lines.push(`f ${a}/${a}/${a} ${b}/${b}/${b} ${c}/${c}/${c}`);
}
fs.writeFileSync(path.join(outputRoot, "dallas_mask.obj"), `${lines.join("\n")}\n`);
fs.writeFileSync(path.join(outputRoot, "dallas_mask.mtl"), [
    "# Dallas mask material",
    "newmtl dallas_mask",
    "Ka 1.000000 1.000000 1.000000",
    "Kd 1.000000 1.000000 1.000000",
    "Ks 0.000000 0.000000 0.000000",
    "d 1.000000",
    "illum 2",
    "map_Kd dallas_mask.png",
    ""
].join("\n"));

const image = json.images[json.textures[0].source];
const imageView = json.bufferViews[image.bufferView];
const imageStart = binaryOffset + (imageView.byteOffset ?? 0);
fs.writeFileSync(
    path.join(outputRoot, "dallas_mask.png"),
    source.subarray(imageStart, imageStart + imageView.byteLength)
);

console.log(JSON.stringify({ hierarchy, sourceBounds: { minimum, maximum, size }, outputRoot }, null, 2));
