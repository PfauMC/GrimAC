package ac.grim.grimac.predictionengine;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.math.Vector3dm;

import java.util.Arrays;
import java.util.UUID;

/**
 * Every finished movement prediction, for a replay recorder: what Grim predicted, which variant of the
 * input it picked and how far off the player was. One subscriber; without one each hook point costs a
 * volatile read.
 */
public final class PredictionTap {

    public static final int WAS_CHECKED = 1;
    public static final int BLOCK_OFFSETS = 1 << 1;
    public static final int KNOCKBACK = 1 << 2;
    public static final int FIRST_BREAD_KB = 1 << 3;
    public static final int EXPLOSION = 1 << 4;
    public static final int FIRST_BREAD_EXPLOSION = 1 << 5;
    public static final int TRIDENT = 1 << 6;
    public static final int ZERO_POINT_ZERO_THREE = 1 << 7;
    public static final int SWIM_HOP = 1 << 8;
    public static final int FLIP_SNEAKING = 1 << 9;
    public static final int FLIP_ITEM = 1 << 10;
    public static final int JUMP = 1 << 11;
    public static final int ATTACK_SLOW = 1 << 12;
    public static final int INPUT_PRESENT = 1 << 13;

    /** Slots of {@link Listener#onPrediction}'s values. */
    public static final int INPUT_X = 0, INPUT_Z = 1, RAW_OFFSET = 2, OFFSET = 3, PREDICTED_X = 4, PREDICTED_Y = 5, PREDICTED_Z = 6,
            X_NEGATIVE = 7, X_POSITIVE = 8, Y_NEGATIVE = 9, Y_POSITIVE = 10, Z_NEGATIVE = 11, Z_POSITIVE = 12, VALUES = 13;

    public interface Listener {
        /**
         * Called on the thread that handles the movement packet, before the checks see the prediction: must
         * neither throw nor block. {@code values} is reused for the next prediction, copy what is kept.
         *
         * @param flags      the bits above
         * @param vectorType the name of the {@link VectorData.VectorType} picked
         * @param values     input, the offset before and after reduction, the predicted movement of this tick and
         *                   the uncertainty in each direction, at the slots above
         */
        void onPrediction(UUID player, int flags, String vectorType, double[] values);
    }

    /** Null when nobody listens; a subscriber sets it back to null when it goes away. */
    public static volatile Listener listener;

    private PredictionTap() {
    }

    /** After the travel of a tick, before the uncertainty is reset for the next one. */
    public static void captureUncertainty(GrimPlayer player) {
        if (listener == null) return;
        UncertaintyHandler u = player.uncertaintyHandler;
        double[] v = player.predictionTapValues;
        v[X_NEGATIVE] = u.xNegativeUncertainty;
        v[X_POSITIVE] = u.xPositiveUncertainty;
        v[Y_NEGATIVE] = u.yNegativeUncertainty;
        v[Y_POSITIVE] = u.yPositiveUncertainty;
        v[Z_NEGATIVE] = u.zNegativeUncertainty;
        v[Z_POSITIVE] = u.zPositiveUncertainty;
    }

    /** The offset as measured, before the uncertainty reduces it. */
    public static void captureRawOffset(GrimPlayer player, double offset) {
        if (listener == null) return;
        player.predictionTapValues[RAW_OFFSET] = offset;
    }

    /** The prediction is final: offset reduced and zeroed for a pending setback, the checks not run yet. */
    public static void fire(GrimPlayer player, double offset, boolean wasChecked) {
        Listener l = listener;
        if (l == null) return;
        double[] v = player.predictionTapValues;
        VectorData picked = player.predictedVelocity;
        int flags = flags(picked, wasChecked, player.getSetbackTeleportUtil().blockOffsets);
        Vector3dm input = picked.input;
        v[INPUT_X] = input == null ? 0 : input.getX();
        v[INPUT_Z] = input == null ? 0 : input.getZ();
        v[OFFSET] = offset;
        v[PREDICTED_X] = picked.vector.getX();
        v[PREDICTED_Y] = picked.vector.getY();
        v[PREDICTED_Z] = picked.vector.getZ();
        try {
            l.onPrediction(player.uuid, flags, picked.vectorType.name(), v);
        } catch (Throwable t) {
            // A listener left over from a reloaded plugin throws NoClassDefFoundError; PacketEvents would kick the player.
            listener = null;
            LogUtil.warn("Prediction listener removed after it threw", t);
        }
        // A tick without travel (dead, spectating, teleported) must not carry this tick's uncertainty.
        Arrays.fill(v, 0);
    }

    private static int flags(VectorData picked, boolean wasChecked, boolean blockOffsets) {
        int flags = 0;
        if (wasChecked) flags |= WAS_CHECKED;
        if (blockOffsets) flags |= BLOCK_OFFSETS;
        if (picked.isKnockback()) flags |= KNOCKBACK;
        if (picked.isFirstBreadKb()) flags |= FIRST_BREAD_KB;
        if (picked.isExplosion()) flags |= EXPLOSION;
        if (picked.isFirstBreadExplosion()) flags |= FIRST_BREAD_EXPLOSION;
        if (picked.isTrident()) flags |= TRIDENT;
        if (picked.isZeroPointZeroThree()) flags |= ZERO_POINT_ZERO_THREE;
        if (picked.isSwimHop()) flags |= SWIM_HOP;
        if (picked.isFlipSneaking()) flags |= FLIP_SNEAKING;
        if (picked.isFlipItem()) flags |= FLIP_ITEM;
        if (picked.isJump()) flags |= JUMP;
        if (picked.isAttackSlow()) flags |= ATTACK_SLOW;
        if (picked.input != null) flags |= INPUT_PRESENT;
        return flags;
    }
}
