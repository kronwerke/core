package de.kronwerke.core.perf;

/** What EmptyCurios remembers on a living entity. Implemented by LivingEntity through a mixin. */
public interface CurioState {

    int kronwerke$curioCheckedAt();

    boolean kronwerke$curioEmpty();

    void kronwerke$setCurioState(int checkedAt, boolean empty);
}
