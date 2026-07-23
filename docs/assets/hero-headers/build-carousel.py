#!/usr/bin/env python3
"""Build the lossless UBS hero APNG without external image dependencies."""

from __future__ import annotations

import binascii
import struct
from argparse import ArgumentParser
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
FRAME_SECONDS = 8
OUTPUT_NAME = "ubs-hero-carousel.png"
FRAME_NAMES = (
    "ubs-hero-overview.png",
    "ubs-hero-bankops.png",
    "ubs-hero-mobile.png",
    "ubs-hero-shop.png",
    "ubs-hero-cash.png",
    "ubs-hero-claims.png",
    "ubs-hero-heist.png",
)


def read_chunks(path: Path) -> list[tuple[bytes, bytes]]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"{path.name} is not a PNG")

    chunks: list[tuple[bytes, bytes]] = []
    offset = len(PNG_SIGNATURE)
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        chunks.append((chunk_type, chunk_data))
        offset += length + 12
        if chunk_type == b"IEND":
            break
    return chunks


def make_chunk(chunk_type: bytes, data: bytes) -> bytes:
    checksum = binascii.crc32(chunk_type)
    checksum = binascii.crc32(data, checksum) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", checksum)


def build_carousel(directory: Path, output_path: Path) -> Path:
    frames = [read_chunks(directory / name) for name in FRAME_NAMES]
    headers = [next(data for kind, data in frame if kind == b"IHDR") for frame in frames]
    if any(header != headers[0] for header in headers[1:]):
        raise ValueError("All carousel frames must have identical PNG dimensions and color settings")

    width, height = struct.unpack_from(">II", headers[0])
    first_idat = next(i for i, (kind, _) in enumerate(frames[0]) if kind == b"IDAT")
    pre_image_chunks = [
        (kind, data)
        for kind, data in frames[0][1:first_idat]
        if kind not in {b"acTL", b"fcTL", b"fdAT"}
    ]

    output = bytearray(PNG_SIGNATURE)
    output.extend(make_chunk(b"IHDR", headers[0]))
    for kind, data in pre_image_chunks:
        output.extend(make_chunk(kind, data))
    output.extend(make_chunk(b"acTL", struct.pack(">II", len(frames), 0)))

    sequence = 0
    for frame_index, frame in enumerate(frames):
        frame_control = struct.pack(
            ">IIIIIHHBB",
            sequence,
            width,
            height,
            0,
            0,
            FRAME_SECONDS,
            1,
            0,
            0,
        )
        output.extend(make_chunk(b"fcTL", frame_control))
        sequence += 1

        idat_chunks = [data for kind, data in frame if kind == b"IDAT"]
        for compressed_data in idat_chunks:
            if frame_index == 0:
                output.extend(make_chunk(b"IDAT", compressed_data))
            else:
                output.extend(make_chunk(b"fdAT", struct.pack(">I", sequence) + compressed_data))
                sequence += 1

    output.extend(make_chunk(b"IEND", b""))
    output_path.write_bytes(output)
    return output_path


if __name__ == "__main__":
    default_directory = Path(__file__).resolve().parent
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=default_directory)
    parser.add_argument("--output", type=Path, default=default_directory / OUTPUT_NAME)
    arguments = parser.parse_args()
    target = build_carousel(arguments.source.resolve(), arguments.output.resolve())
    print(f"Built {target.name} with {len(FRAME_NAMES)} frames at {FRAME_SECONDS}s per frame")
