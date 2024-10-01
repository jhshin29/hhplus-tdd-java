package io.hhplus.tdd.point.reponse;

public record UserPoint(
        long id,
        long point,
        long updateMillis
) {

    public static UserPoint empty(long id) {
        return new UserPoint(id, 0, System.currentTimeMillis());
    }

    public UserPoint chargeOrUsePoint(long amount) {
        return new UserPoint(this.id, this.point + amount, System.currentTimeMillis());
    }
}
