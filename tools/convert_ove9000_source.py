#!/usr/bin/env python3
"""Convert the supplied Source Engine OVE9000 model into OBJ and PNG assets.

This tool intentionally performs only a technical format conversion. It keeps the
source geometry, UVs, normals, and diffuse textures unchanged.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


@dataclass(frozen=True)
class ConvertedPart:
    name: str
    vertices: np.ndarray
    faces: np.ndarray
    materials: np.ndarray
    material_names: tuple[str, ...]


def safe_name(value: str) -> str:
    return re.sub(r"[^a-z0-9_]+", "_", value.lower()).strip("_")


def merge_strip_groups(vtx_mesh):
    index_groups = []
    vertex_groups = []
    vertex_offset = 0
    for strip_group in vtx_mesh.strip_groups:
        index_groups.append(np.add(strip_group.indices, vertex_offset))
        vertex_groups.append(
            strip_group.vertexes["original_mesh_vertex_index"].reshape(-1).astype(np.uint32)
        )
        vertex_offset += sum(strip.vertex_count for strip in strip_group.strips)
    if not index_groups:
        return np.array([], dtype=np.uint32), np.array([], dtype=np.uint32), 0
    return np.hstack(index_groups), np.hstack(vertex_groups), vertex_offset


def merge_meshes(model, vtx_model):
    vertex_ids = []
    face_materials = []
    index_groups = []
    vertex_offset = 0
    for vtx_mesh, mesh in zip(vtx_model.meshes, model.meshes):
        if not vtx_mesh.strip_groups:
            continue
        indices, vertices, count = merge_strip_groups(vtx_mesh)
        index_groups.append(np.add(indices, vertex_offset))
        vertex_ids.extend(np.add(vertices, mesh.vertex_index_start))
        face_materials.append(np.full(indices.shape[0] // 3, mesh.material_index, dtype=np.uint32))
        vertex_offset += count
    if not index_groups:
        return np.array([], dtype=np.uint32), np.array([], dtype=np.uint32), np.array([], dtype=np.uint32)
    return np.asarray(vertex_ids, dtype=np.uint32), np.hstack(index_groups), np.hstack(face_materials)


def load_sourceio(sourceio_root: Path):
    sys.path.insert(0, str(sourceio_root.parent))
    from SourceIO.library.models.mdl.v49.mdl_file import MdlV49
    from SourceIO.library.models.vtx import open_vtx
    from SourceIO.library.models.vvd import Vvd
    from SourceIO.library.source1.vtf import load_texture
    from SourceIO.library.utils import FileBuffer

    return MdlV49, open_vtx, Vvd, load_texture, FileBuffer


def inspect_model(label: str, mdl, vtx) -> None:
    print(f"{label}: MDL v{mdl.header.version}, {len(mdl.body_parts)} body parts")
    print("  materials:", ", ".join(material.name for material in mdl.materials))
    for body_index, (body, vtx_body) in enumerate(zip(mdl.body_parts, vtx.body_parts)):
        models = []
        for model_index, (model, vtx_model) in enumerate(zip(body.models, vtx_body.models)):
            lod_meshes = len(vtx_model.model_lods[0].meshes) if vtx_model.model_lods else 0
            models.append(f"{model_index}:{model.name} ({model.vertex_count} vertices, {lod_meshes} meshes)")
        print(f"  body[{body_index}] {body.name}: " + "; ".join(models))


def load_model_files(stem: Path, sourceio):
    MdlV49, open_vtx, Vvd, _, FileBuffer = sourceio
    mdl = MdlV49.from_buffer(FileBuffer(str(stem.with_suffix(".mdl"))))
    vtx = open_vtx(FileBuffer(str(stem.with_suffix(".dx90.vtx"))))
    vvd = Vvd.from_buffer(FileBuffer(str(stem.with_suffix(".vvd"))))
    return mdl, vtx, vvd


def extract_default_parts(label: str, mdl, vtx, vvd) -> list[ConvertedPart]:
    parts: list[ConvertedPart] = []
    all_vertices = vvd.lod_data[0]
    material_names = tuple(material.name for material in mdl.materials)

    for body, vtx_body in zip(mdl.body_parts, vtx.body_parts):
        if not body.models or not vtx_body.models:
            continue
        model, vtx_model = body.models[0], vtx_body.models[0]
        if model.vertex_count == 0 or not vtx_model.model_lods or not vtx_model.model_lods[0].meshes:
            continue
        model_vertices = all_vertices[model.vertex_offset : model.vertex_offset + model.vertex_count]
        vertex_ids, indices, face_materials = merge_meshes(model, vtx_model.model_lods[0])
        if indices.size == 0:
            continue
        vertices = model_vertices[vertex_ids]
        faces = np.flip(indices).reshape((-1, 3))
        parts.append(
            ConvertedPart(
                f"{label}_{safe_name(body.name)}",
                vertices,
                faces,
                face_materials,
                material_names,
            )
        )
    return parts


def find_diffuse_texture(material_root: Path, material_name: str) -> Path:
    direct = material_root / f"{material_name}.vtf"
    if direct.is_file():
        return direct
    target = material_name.casefold()
    matches = [path for path in material_root.rglob("*.vtf") if path.stem.casefold() == target]
    if not matches:
        raise FileNotFoundError(f"No VTF found for material {material_name!r}")
    return matches[0]


def export_textures(parts: list[ConvertedPart], material_root: Path, output: Path, sourceio) -> dict[str, str]:
    _, _, _, load_texture, FileBuffer = sourceio
    used = {
        part.material_names[int(material_id)]
        for part in parts
        for material_id in np.unique(part.materials)
    }
    exported: dict[str, str] = {}
    for material_name in sorted(used):
        source = find_diffuse_texture(material_root, material_name)
        pixels, height, width = load_texture(FileBuffer(str(source)))
        if pixels is None:
            raise RuntimeError(f"Could not decode {source}")
        rgba = np.clip(pixels * 255.0, 0, 255).astype(np.uint8)
        # PAYDAY stores material-mask data in diffuse alpha; it is not opacity.
        rgba[:, :, 3] = 255
        image = Image.fromarray(rgba, "RGBA")
        texture_stem = safe_name(material_name)
        if texture_stem.endswith("_diffuse"):
            texture_stem = texture_stem.removesuffix("_diffuse")
        texture_name = f"{texture_stem}.png"
        image.save(output / texture_name, optimize=True)
        exported[material_name] = texture_name
        print(f"texture {material_name}: {width}x{height} -> {texture_name}")
    return exported


def export_obj(parts: list[ConvertedPart], textures: dict[str, str], output: Path) -> None:
    obj_path = output / "ove9000_saw.obj"
    mtl_path = output / "ove9000_saw.mtl"

    with mtl_path.open("w", encoding="ascii", newline="\n") as mtl:
        for material_name, texture_name in textures.items():
            name = safe_name(material_name)
            mtl.write(f"newmtl {name}\nKd 1.000000 1.000000 1.000000\n")
            mtl.write(f"map_Kd ultimatebankingsystem:item/{Path(texture_name).stem}\n\n")

    source_positions = np.concatenate([part.vertices["vertex"] for part in parts])
    minecraft_positions = np.column_stack(
        (source_positions[:, 0], source_positions[:, 2], -source_positions[:, 1])
    )
    source_center = (minecraft_positions.min(axis=0) + minecraft_positions.max(axis=0)) * 0.5
    source_extent = minecraft_positions.max(axis=0) - minecraft_positions.min(axis=0)
    model_scale = 0.9 / float(source_extent.max())

    with obj_path.open("w", encoding="ascii", newline="\n") as obj:
        obj.write("# OVE9000 technical format conversion for Ultimate Banking System\n")
        obj.write("mtllib ove9000_saw.mtl\n")
        offset = 1
        for part in parts:
            obj.write(f"\no {part.name}\n")
            for vertex in part.vertices["vertex"]:
                converted = np.array((vertex[0], vertex[2], -vertex[1]))
                converted = (converted - source_center) * model_scale + 0.5
                obj.write(f"v {converted[0]:.7f} {converted[1]:.7f} {converted[2]:.7f}\n")
            for uv in part.vertices["uv"]:
                obj.write(f"vt {uv[0]:.7f} {1.0 - uv[1]:.7f}\n")
            for normal in part.vertices["normal"]:
                obj.write(f"vn {normal[0]:.7f} {normal[2]:.7f} {-normal[1]:.7f}\n")

            current_material = None
            for face, material_id in zip(part.faces, part.materials):
                material = safe_name(part.material_names[int(material_id)])
                if material != current_material:
                    obj.write(f"usemtl {material}\n")
                    current_material = material
                indices = [int(index) + offset for index in face]
                obj.write("f " + " ".join(f"{index}/{index}/{index}" for index in indices) + "\n")
            offset += len(part.vertices)

    final_positions = (minecraft_positions - source_center) * model_scale + 0.5
    print(f"OBJ vertices={len(final_positions)}, faces={sum(len(part.faces) for part in parts)}")
    print(f"bounds min={final_positions.min(axis=0)}, max={final_positions.max(axis=0)}")
    print(f"wrote {obj_path}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="PAYDAY 2 - OVE9000 Saw package root")
    parser.add_argument("output", type=Path)
    parser.add_argument("--sourceio", type=Path, required=True)
    parser.add_argument("--inspect-only", action="store_true")
    args = parser.parse_args()

    sourceio = load_sourceio(args.sourceio.resolve())
    model_root = args.source / "models" / "JohnSheppard44" / "PAYDAY 2" / "Weapons" / "Misc"
    material_root = args.source / "materials"
    saw = load_model_files(model_root / "OVE9000 Saw", sourceio)
    inspect_model("saw", saw[0], saw[1])
    if args.inspect_only:
        return 0

    args.output.mkdir(parents=True, exist_ok=True)
    parts = extract_default_parts("saw", *saw)
    textures = export_textures(parts, material_root, args.output, sourceio)
    export_obj(parts, textures, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
