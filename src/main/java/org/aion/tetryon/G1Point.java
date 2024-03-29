package org.aion.tetryon;

import java.math.BigInteger;

/**
 * Represents a point on G1.
 */
public class G1Point {
    public final Fp x;
    public final Fp y;

    public G1Point(String x, String y) {
        this.x = new Fp(new BigInteger(x, 16));
        this.y = new Fp(new BigInteger(y, 16));
    }

    public G1Point(Fp x, Fp y) {
        this.x = x;
        this.y = y;
    }

    public boolean isZero() {
        return x.isZero() && y.isZero();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        G1Point that = (G1Point) o;
        return this.x.equals(that.x) && this.y.equals(that.y);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + this.x.hashCode();
        result = 31 * result + this.y.hashCode();

        return result;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
