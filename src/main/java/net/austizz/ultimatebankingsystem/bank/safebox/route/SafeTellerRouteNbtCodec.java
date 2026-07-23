package net.austizz.ultimatebankingsystem.bank.safebox.route;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SafeTellerRouteNbtCodec {
    private static final String STEPS_KEY = "steps";

    private SafeTellerRouteNbtCodec() {
    }

    static Optional<SafeTellerRoute> decode(CompoundTag tag) {
        if (tag == null || !strings(tag, "id", "bankId", "vaultId", "tellerId", "direction", "dimension")
                || !coordinates(tag, "start") || !coordinates(tag, "finish")) {
            return Optional.empty();
        }
        ListTag stepTags = compoundList(tag, STEPS_KEY);
        if (stepTags == null || stepTags.size() > SafeTellerRouteValidator.MAX_STEPS) {
            return Optional.empty();
        }
        List<SafeTellerRouteStep> steps = new ArrayList<>(stepTags.size());
        for (int i = 0; i < stepTags.size(); i++) {
            SafeTellerRouteStep step = decodeStep(stepTags.getCompound(i));
            if (step == null) {
                return Optional.empty();
            }
            steps.add(step);
        }
        SafeTellerRoute route = new SafeTellerRoute(
                tag.getString("id"),
                tag.getString("bankId"),
                tag.getString("vaultId"),
                tag.getString("tellerId"),
                SafeTellerRouteDirection.parse(tag.getString("direction")),
                tag.getString("dimension"),
                position(tag, "start"),
                position(tag, "finish"),
                steps
        );
        return SafeTellerRouteValidator.validate(route).valid() ? Optional.of(route) : Optional.empty();
    }

    static CompoundTag encode(SafeTellerRoute route, CompoundTag existing) {
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copy();
        tag.putString("id", route.id());
        tag.putString("bankId", route.bankId());
        tag.putString("vaultId", route.vaultId());
        tag.putString("tellerId", route.tellerId());
        tag.putString("direction", route.direction().name());
        tag.putString("dimension", route.dimension());
        putPosition(tag, "start", route.start());
        putPosition(tag, "finish", route.finish());
        ListTag oldSteps = compoundList(tag, STEPS_KEY);
        ListTag steps = new ListTag();
        for (int i = 0; i < route.steps().size(); i++) {
            CompoundTag oldStep = oldSteps != null && i < oldSteps.size() ? oldSteps.getCompound(i) : null;
            steps.add(encodeStep(route.steps().get(i), oldStep));
        }
        tag.put(STEPS_KEY, steps);
        return tag;
    }

    private static SafeTellerRouteStep decodeStep(CompoundTag tag) {
        if (tag == null || !tag.contains("type", Tag.TAG_STRING)) {
            return null;
        }
        return switch (tag.getString("type")) {
            case "WALK" -> coordinates(tag, "")
                    ? new SafeTellerRouteStep.Walk(position(tag, "")) : null;
            case "WAIT" -> numeric(tag, "durationTicks")
                    ? new SafeTellerRouteStep.Wait(tag.getInt("durationTicks")) : null;
            case "REDSTONE" -> decodeRedstone(tag);
            case "RFID" -> coordinates(tag, "")
                    ? new SafeTellerRouteStep.Rfid(position(tag, "")) : null;
            default -> null;
        };
    }

    private static SafeTellerRouteStep decodeRedstone(CompoundTag tag) {
        if (!coordinates(tag, "") || !tag.contains("face", Tag.TAG_STRING)
                || !numeric(tag, "strength") || !numeric(tag, "durationTicks")) {
            return null;
        }
        SafeTellerRouteFace face = SafeTellerRouteFace.parse(tag.getString("face"));
        return face == null ? null : new SafeTellerRouteStep.Redstone(
                position(tag, ""), face, tag.getInt("strength"), tag.getInt("durationTicks"));
    }

    private static CompoundTag encodeStep(SafeTellerRouteStep step, CompoundTag existing) {
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copy();
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        tag.remove("face");
        tag.remove("strength");
        tag.remove("durationTicks");
        if (step instanceof SafeTellerRouteStep.Walk walk) {
            tag.putString("type", "WALK");
            putPosition(tag, "", walk.target());
        } else if (step instanceof SafeTellerRouteStep.Wait wait) {
            tag.putString("type", "WAIT");
            tag.putInt("durationTicks", wait.durationTicks());
        } else if (step instanceof SafeTellerRouteStep.Redstone redstone) {
            tag.putString("type", "REDSTONE");
            putPosition(tag, "", redstone.target());
            tag.putString("face", redstone.face().name());
            tag.putInt("strength", redstone.strength());
            tag.putInt("durationTicks", redstone.durationTicks());
        } else if (step instanceof SafeTellerRouteStep.Rfid rfid) {
            tag.putString("type", "RFID");
            putPosition(tag, "", rfid.scanner());
        }
        return tag;
    }

    private static ListTag compoundList(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return null;
        }
        return list;
    }

    private static boolean strings(CompoundTag tag, String... keys) {
        for (String key : keys) {
            if (!tag.contains(key, Tag.TAG_STRING)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coordinates(CompoundTag tag, String prefix) {
        return numeric(tag, coordinateKey(prefix, "X"))
                && numeric(tag, coordinateKey(prefix, "Y"))
                && numeric(tag, coordinateKey(prefix, "Z"));
    }

    private static boolean numeric(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT);
    }

    private static SafeTellerRoutePosition position(CompoundTag tag, String prefix) {
        return new SafeTellerRoutePosition(tag.getInt(coordinateKey(prefix, "X")),
                tag.getInt(coordinateKey(prefix, "Y")), tag.getInt(coordinateKey(prefix, "Z")));
    }

    private static void putPosition(CompoundTag tag, String prefix, SafeTellerRoutePosition position) {
        tag.putInt(coordinateKey(prefix, "X"), position.x());
        tag.putInt(coordinateKey(prefix, "Y"), position.y());
        tag.putInt(coordinateKey(prefix, "Z"), position.z());
    }

    private static String coordinateKey(String prefix, String axis) {
        return prefix.isEmpty() ? axis.toLowerCase(java.util.Locale.ROOT) : prefix + axis;
    }
}
