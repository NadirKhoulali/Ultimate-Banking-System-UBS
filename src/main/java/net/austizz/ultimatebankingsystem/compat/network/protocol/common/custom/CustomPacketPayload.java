package net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 1.21-style payload marker shim for Forge 1.20.1.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final ResourceLocation id;

        public Type(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public ResourceLocation id() {
            return id;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Type<?> other)) {
                return false;
            }
            return id.equals(other.id);
        }
    }
}
