package io.livelattice.notifications.delivery;

public record DeliveryResult(
    boolean successful,
    String error
) {
    public static DeliveryResult success() {
        return new DeliveryResult(true, null);
    }

    public static DeliveryResult failure(String error) {
        return new DeliveryResult(false, error);
    }
}
