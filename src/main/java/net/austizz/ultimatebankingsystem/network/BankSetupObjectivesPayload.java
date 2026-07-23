package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record BankSetupObjectivesPayload(List<Project> projects) implements CustomPacketPayload {
    private static final int MAX_PROJECTS = 16;

    public static final Type<BankSetupObjectivesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_setup_objectives"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankSetupObjectivesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        List<Project> values = payload == null ? List.of() : payload.projects();
                        int count = Math.min(MAX_PROJECTS, values.size());
                        buf.writeVarInt(count);
                        for (int i = 0; i < count; i++) {
                            Project.STREAM_CODEC.encode(buf, values.get(i));
                        }
                    },
                    buf -> {
                        int count = Math.max(0, Math.min(MAX_PROJECTS, buf.readVarInt()));
                        List<Project> values = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            values.add(Project.STREAM_CODEC.decode(buf));
                        }
                        return new BankSetupObjectivesPayload(values);
                    }
            );

    public BankSetupObjectivesPayload {
        projects = projects == null
                ? List.of()
                : List.copyOf(projects.stream().filter(java.util.Objects::nonNull).limit(MAX_PROJECTS).toList());
    }

    public String signature() {
        return projects.toString();
    }

    @Override
    public Type<BankSetupObjectivesPayload> type() {
        return TYPE;
    }

    public record Project(String projectId,
                          String projectName,
                          int step,
                          int totalSteps,
                          String objectiveTitle,
                          String objectiveDetail) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Project> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.projectId(), 64);
                    buf.writeUtf(value.projectName(), 72);
                    buf.writeVarInt(value.step());
                    buf.writeVarInt(value.totalSteps());
                    buf.writeUtf(value.objectiveTitle(), 128);
                    buf.writeUtf(value.objectiveDetail(), 384);
                },
                buf -> new Project(
                        buf.readUtf(64),
                        buf.readUtf(72),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readUtf(128),
                        buf.readUtf(384)
                )
        );

        public Project {
            projectId = trim(projectId, 64);
            projectName = trim(projectName, 72);
            totalSteps = Math.max(1, Math.min(16, totalSteps));
            step = Math.max(1, Math.min(totalSteps, step));
            objectiveTitle = trim(objectiveTitle, 128);
            objectiveDetail = trim(objectiveDetail, 384);
        }

        private static String trim(String value, int maxLength) {
            String safe = value == null ? "" : value.trim();
            return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
        }
    }
}
