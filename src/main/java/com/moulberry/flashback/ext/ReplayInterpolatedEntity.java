package com.moulberry.flashback.ext;
/** Flushes client interpolation after a replay seek without advancing entity simulation. */
public interface ReplayInterpolatedEntity {
    void flashback$finishInterpolation();
}
