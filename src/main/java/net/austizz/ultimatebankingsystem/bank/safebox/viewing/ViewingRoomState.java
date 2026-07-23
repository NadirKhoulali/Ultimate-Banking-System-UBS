package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import java.util.List;

public record ViewingRoomState(ViewingRoomSnapshot room,
                               ViewingRoomStatus status,
                               List<String> reasons) {
    public ViewingRoomState {
        if (room == null || status == null) {
            throw new IllegalArgumentException("Viewing room state requires a room and status.");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean ready() {
        return status == ViewingRoomStatus.READY;
    }
}
