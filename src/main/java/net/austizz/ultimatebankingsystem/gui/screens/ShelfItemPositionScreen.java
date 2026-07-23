package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayType;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfTransformBounds;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.austizz.ultimatebankingsystem.client.renderer.ModularWallDisplayRenderer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.ShelfActionPayload;
import net.austizz.ultimatebankingsystem.network.ShelfOpenPayload;
import net.austizz.ultimatebankingsystem.network.ShelfSlotSummary;
import net.austizz.ultimatebankingsystem.network.ShelfUnitSummary;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class ShelfItemPositionScreen extends Screen {
    private final ShelfOpenPayload payload;
    private final ShelfUnitSummary shelf;
    private final int slotIndex;
    private final int absoluteSlotIndex;
    private final BlockPos shelfPos;
    private final ShelfTransformBounds bounds;
    private final ItemDisplayTransform originalTransform;

    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float scaleX;
    private float scaleY;
    private float scaleZ;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int gizmoCenterX;
    private int gizmoCenterY;
    private int gizmoRadius;
    private int valueFieldStartX;
    private int valueFieldStartY;
    private int valueFieldWidth;
    private int valueFieldHeight;
    private int valueLabelX;

    private ToolMode mode = ToolMode.MOVE;
    private Axis axis = Axis.X;
    private boolean draggingAxis;
    private double lastDragMouseX;
    private double lastDragMouseY;
    private boolean saveRequested;

    private DesktopButton moveModeButton;
    private DesktopButton rotateModeButton;
    private DesktopButton scaleModeButton;
    private DesktopButton axisXButton;
    private DesktopButton axisYButton;
    private DesktopButton axisZButton;
    private DesktopButton applyValuesButton;
    private DesktopButton resetButton;
    private DesktopButton saveButton;
    private DesktopButton cancelButton;

    private final DesktopEditBox[] valueFields = new DesktopEditBox[9];
    private final String[] valueLabels = new String[]{"Move X", "Move Y", "Move Z", "Rot X", "Rot Y", "Rot Z", "Scale X", "Scale Y", "Scale Z"};

    private boolean feedbackSuccess = true;
    private String feedbackMessage = "";
    private long feedbackUntilMillis;
    private Entity previousCameraEntity;
    private Entity editorCameraEntity;
    private boolean editorCameraActive;
    private boolean spectatorModeRequested;

    public ShelfItemPositionScreen(ShelfOpenPayload payload, ShelfUnitSummary shelf, ShelfSlotSummary slot) {
        super(UbsTranslations.literal("Shelf Item Transform"));
        this.payload = payload;
        this.shelf = shelf;
        this.slotIndex = slot == null ? 0 : slot.slotIndex();
        ParsedShelfTarget parsedTarget = parseShelfTarget(shelf == null ? null : shelf.posKey());
        this.shelfPos = parsedTarget.pos();
        // Use server-provided absolute slot mapping to keep camera/preview pinned to the exact edited item.
        this.absoluteSlotIndex = slot == null
                ? Math.max(0, this.slotIndex)
                : Math.max(0, slot.absoluteSlotIndex());
        this.bounds = ShelfTransformBounds.forTypeId(shelf == null ? ShelfDisplayType.UNKNOWN.id() : shelf.displayType());

        this.originalTransform = slot == null
                ? ItemDisplayTransform.DEFAULT
                : new ItemDisplayTransform(
                slot.offsetX(),
                slot.offsetY(),
                slot.offsetZ(),
                slot.rotationX(),
                slot.rotationY(),
                slot.rotationZ(),
                slot.scaleX(),
                slot.scaleY(),
                slot.scaleZ()
        );
        applyTransform(this.originalTransform, false);
    }

    @Override
    protected void init() {
        clearWidgets();

        panelWidth = Math.min(780, Math.max(610, width - 24));
        panelHeight = Math.min(430, Math.max(350, height - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        // Keep the gizmo anchor at the exact screen center so it matches the camera aim point.
        gizmoCenterX = width / 2;
        gizmoCenterY = height / 2;
        gizmoRadius = Mth.clamp(Math.min(width, height) / 9, 52, 90);

        int topRowY = panelTop + 42;
        int modeButtonW = 86;
        int modeGap = 6;
        int modeStartX = panelLeft + 14;
        moveModeButton = addRenderableWidget(new DesktopButton(
                modeStartX,
                topRowY,
                modeButtonW,
                22,
                UbsTranslations.literal("Move (W)"),
                0xFF8FB8E5,
                btn -> setMode(ToolMode.MOVE)
        ));
        rotateModeButton = addRenderableWidget(new DesktopButton(
                modeStartX + modeButtonW + modeGap,
                topRowY,
                modeButtonW,
                22,
                UbsTranslations.literal("Rotate (E)"),
                0xFF8FB8E5,
                btn -> setMode(ToolMode.ROTATE)
        ));
        scaleModeButton = addRenderableWidget(new DesktopButton(
                modeStartX + (modeButtonW + modeGap) * 2,
                topRowY,
                modeButtonW,
                22,
                UbsTranslations.literal("Scale (R)"),
                0xFF8FB8E5,
                btn -> setMode(ToolMode.SCALE)
        ));

        int axisStartX = modeStartX + (modeButtonW + modeGap) * 3 + 12;
        int axisButtonW = 48;
        axisXButton = addRenderableWidget(new DesktopButton(
                axisStartX,
                topRowY,
                axisButtonW,
                22,
                UbsTranslations.literal("X"),
                axisColor(Axis.X, false),
                btn -> setAxis(Axis.X)
        ));
        axisYButton = addRenderableWidget(new DesktopButton(
                axisStartX + axisButtonW + modeGap,
                topRowY,
                axisButtonW,
                22,
                UbsTranslations.literal("Y"),
                axisColor(Axis.Y, false),
                btn -> setAxis(Axis.Y)
        ));
        axisZButton = addRenderableWidget(new DesktopButton(
                axisStartX + (axisButtonW + modeGap) * 2,
                topRowY,
                axisButtonW,
                22,
                UbsTranslations.literal("Z"),
                axisColor(Axis.Z, false),
                btn -> setAxis(Axis.Z)
        ));

        // Keep manual entry fields compact, with labels to the left.
        valueFieldWidth = 88;
        valueFieldHeight = 16;
        valueFieldStartX = panelLeft + panelWidth - valueFieldWidth - 14;
        valueLabelX = valueFieldStartX - 58;
        valueFieldStartY = panelTop + 84;
        addValueFields();

        // Use two button rows so Apply/Save/Cancel never overlap on narrow screens.
        int actionStartX = panelLeft + panelWidth - 186;
        int actionRowOneY = panelTop + panelHeight - 58;
        int actionRowTwoY = actionRowOneY + 24;
        applyValuesButton = addRenderableWidget(new DesktopButton(
                actionStartX,
                actionRowOneY,
                90,
                22,
                UbsTranslations.literal("Apply"),
                0xFF8BDCB3,
                btn -> applyValuesFromFields()
        ));
        resetButton = addRenderableWidget(new DesktopButton(
                actionStartX + 96,
                actionRowOneY,
                90,
                22,
                UbsTranslations.literal("Reset"),
                0xFFC2ABEF,
                btn -> resetValues()
        ));
        saveButton = addRenderableWidget(new DesktopButton(
                actionStartX,
                actionRowTwoY,
                90,
                22,
                UbsTranslations.literal("Save"),
                0xFF8AE0AC,
                btn -> saveAndReturn()
        ));
        cancelButton = addRenderableWidget(new DesktopButton(
                actionStartX + 96,
                actionRowTwoY,
                90,
                22,
                UbsTranslations.literal("Cancel"),
                0xFF89B7EE,
                btn -> cancelAndReturn()
        ));

        updateEditorsFromState(true);
        updateControlState();
        activateEditorCamera();
        updateEditorCamera();
        applyPreview();
        if (!spectatorModeRequested) {
            syncSpectatorMode(true);
            spectatorModeRequested = true;
        }
    }

    private void addValueFields() {
        for (int i = 0; i < valueFields.length; i++) {
            int rowY = fieldY(i);
            DesktopEditBox editBox = new DesktopEditBox(
                    font,
                    valueFieldStartX,
                    rowY,
                    valueFieldWidth,
                    valueFieldHeight,
                    UbsTranslations.literal(valueLabels[i])
            );
            editBox.setMaxLength(18);
            // Restrict manual entry to 3 decimal places for consistent precision.
            editBox.setFilter(value -> value == null || value.isEmpty() || value.matches("^-?\\d*(\\.\\d{0,3})?$"));
            valueFields[i] = addRenderableWidget(editBox);
        }
    }

    private int fieldY(int index) {
        int group = index / 3;
        int rowInGroup = index % 3;
        int rowStep = valueFieldHeight + 3;
        int groupGap = 11;
        return valueFieldStartY + group * (rowStep * 3 + groupGap) + rowInGroup * rowStep;
    }

    private void setMode(ToolMode next) {
        mode = next == null ? ToolMode.MOVE : next;
        updateControlState();
    }

    private void setAxis(Axis next) {
        axis = next == null ? Axis.X : next;
        updateControlState();
    }

    private void updateControlState() {
        if (moveModeButton != null) {
            moveModeButton.active = mode != ToolMode.MOVE;
        }
        if (rotateModeButton != null) {
            rotateModeButton.active = mode != ToolMode.ROTATE;
        }
        if (scaleModeButton != null) {
            scaleModeButton.active = mode != ToolMode.SCALE;
        }

        if (axisXButton != null) {
            axisXButton.active = axis != Axis.X;
            axisXButton.setAccentColor(axisColor(Axis.X, axis == Axis.X));
        }
        if (axisYButton != null) {
            axisYButton.active = axis != Axis.Y;
            axisYButton.setAccentColor(axisColor(Axis.Y, axis == Axis.Y));
        }
        if (axisZButton != null) {
            axisZButton.active = axis != Axis.Z;
            axisZButton.setAccentColor(axisColor(Axis.Z, axis == Axis.Z));
        }
    }

    private int axisColor(Axis axis, boolean selected) {
        return switch (axis) {
            case X -> selected ? 0xFFED7B7B : 0xFFB15A5A;
            case Y -> selected ? 0xFF88E2A2 : 0xFF4E9A66;
            case Z -> selected ? 0xFF7DB2FF : 0xFF4D73AF;
        };
    }

    private void applyPreview() {
        if (payload == null || shelfPos == null) {
            return;
        }
        ShelfTransformPreviewClientState.setPreview(
                payload.dimensionId(),
                shelfPos,
                absoluteSlotIndex,
                currentTransform()
        );
    }

    private void clearPreview() {
        if (payload == null || shelfPos == null) {
            return;
        }
        ShelfTransformPreviewClientState.clearPreview(payload.dimensionId(), shelfPos, absoluteSlotIndex);
    }

    private ItemDisplayTransform currentTransform() {
        return new ItemDisplayTransform(
                offsetX,
                offsetY,
                offsetZ,
                rotationX,
                rotationY,
                rotationZ,
                scaleX,
                scaleY,
                scaleZ
        );
    }

    private void applyTransform(ItemDisplayTransform transform, boolean applyPreview) {
        ItemDisplayTransform clamped = bounds.clamp(transform);
        offsetX = round3(clamped.offsetX());
        offsetY = round3(clamped.offsetY());
        offsetZ = round3(clamped.offsetZ());
        rotationX = round3(clamped.rotationX());
        rotationY = round3(clamped.rotationY());
        rotationZ = round3(clamped.rotationZ());
        scaleX = round3(clamped.scaleX());
        scaleY = round3(clamped.scaleY());
        scaleZ = round3(clamped.scaleZ());
        if (applyPreview) {
            applyPreview();
        }
    }

    private void resetValues() {
        applyTransform(ItemDisplayTransform.DEFAULT, true);
        updateEditorsFromState(true);
        setFeedback(true, "Transform reset to default.");
    }

    private void applyValuesFromFields() {
        Float nextOffsetX = parseField(0, offsetX);
        Float nextOffsetY = parseField(1, offsetY);
        Float nextOffsetZ = parseField(2, offsetZ);
        Float nextRotationX = parseField(3, rotationX);
        Float nextRotationY = parseField(4, rotationY);
        Float nextRotationZ = parseField(5, rotationZ);
        Float nextScaleX = parseField(6, scaleX);
        Float nextScaleY = parseField(7, scaleY);
        Float nextScaleZ = parseField(8, scaleZ);
        if (nextOffsetX == null || nextOffsetY == null || nextOffsetZ == null
                || nextRotationX == null || nextRotationY == null || nextRotationZ == null
                || nextScaleX == null || nextScaleY == null || nextScaleZ == null) {
            return;
        }

        applyTransform(new ItemDisplayTransform(
                nextOffsetX,
                nextOffsetY,
                nextOffsetZ,
                nextRotationX,
                nextRotationY,
                nextRotationZ,
                nextScaleX,
                nextScaleY,
                nextScaleZ
        ), true);
        updateEditorsFromState(true);
        setFeedback(true, "Values applied.");
    }

    private Float parseField(int index, float fallback) {
        if (index < 0 || index >= valueFields.length || valueFields[index] == null) {
            return fallback;
        }
        String raw = valueFields[index].getValue();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return round3(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException ex) {
            setFeedback(false, tr("Invalid numeric value in ") + tr(valueLabels[index]) + ".");
            return null;
        }
    }

    private void saveAndReturn() {
        if (payload != null && shelf != null) {
            String encoded = String.format(
                    Locale.ROOT,
                    "%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f",
                    offsetX,
                    offsetY,
                    offsetZ,
                    rotationX,
                    rotationY,
                    rotationZ,
                    scaleX,
                    scaleY,
                    scaleZ
            );
            PacketDistributor.sendToServer(new ShelfActionPayload(
                    payload.dimensionId(),
                    payload.rootX(),
                    payload.rootY(),
                    payload.rootZ(),
                    shelf.posKey(),
                    "save_transform",
                    slotIndex,
                    encoded,
                    -1
            ));
            saveRequested = true;
        }
        returnToShelf();
    }

    private void cancelAndReturn() {
        clearPreview();
        returnToShelf();
    }

    private void returnToShelf() {
        restoreEditorCamera();
        Minecraft.getInstance().setScreen(new ShelfScreen(payload));
    }

    private void setFeedback(boolean success, String message) {
        feedbackSuccess = success;
        feedbackMessage = message == null ? "" : message;
        feedbackUntilMillis = System.currentTimeMillis() + 2200L;
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() > feedbackUntilMillis) {
            feedbackMessage = "";
        }
        updateEditorsFromState(false);
        updateEditorCamera();
    }

    private void updateEditorsFromState(boolean force) {
        setField(0, offsetX, force);
        setField(1, offsetY, force);
        setField(2, offsetZ, force);
        setField(3, rotationX, force);
        setField(4, rotationY, force);
        setField(5, rotationZ, force);
        setField(6, scaleX, force);
        setField(7, scaleY, force);
        setField(8, scaleZ, force);
    }

    private void setField(int index, float value, boolean force) {
        if (index < 0 || index >= valueFields.length || valueFields[index] == null) {
            return;
        }
        if (!force && valueFields[index].isFocused()) {
            return;
        }
        String format = "%.3f";
        valueFields[index].setValue(String.format(Locale.ROOT, format, value));
    }

    private void activateEditorCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || shelfPos == null) {
            return;
        }
        if (editorCameraEntity == null || editorCameraEntity.level() != mc.level || editorCameraEntity.isRemoved()) {
            // Marker entity is lightweight and model-less, ideal for temporary client camera anchoring.
            editorCameraEntity = EntityType.MARKER.create(mc.level);
            if (editorCameraEntity == null) {
                return;
            }
            editorCameraEntity.setNoGravity(true);
            editorCameraEntity.setInvisible(true);
            editorCameraEntity.noPhysics = true;
        }
        if (previousCameraEntity == null || previousCameraEntity == editorCameraEntity) {
            Entity current = mc.getCameraEntity();
            previousCameraEntity = (current != null && current != editorCameraEntity) ? current : mc.player;
        }
        mc.setCameraEntity(editorCameraEntity);
        editorCameraActive = true;
    }

    private void updateEditorCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (!editorCameraActive || mc.level == null || mc.player == null || editorCameraEntity == null || shelfPos == null) {
            return;
        }

        Vec3 target = resolveItemTarget();
        Direction facing = resolveFacing(mc.level.getBlockState(shelfPos));
        Vec3 outward = resolveOutwardVector(facing);
        Vec3 cameraPos = target.add(outward.scale(resolveCameraDistance())).add(0.0D, 0.08D, 0.0D);

        Vec3 look = target.subtract(cameraPos);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, horizontal));

        editorCameraEntity.setPos(cameraPos);   
        editorCameraEntity.setYRot(yaw);
        editorCameraEntity.setXRot(pitch);
        // Pin interpolation origin to the freshly applied camera transform so aim stays exactly centered.
        editorCameraEntity.setOldPosAndRot();
        editorCameraEntity.setDeltaMovement(Vec3.ZERO);

        if (mc.getCameraEntity() != editorCameraEntity) {
            mc.setCameraEntity(editorCameraEntity);
        }
    }

    private void restoreEditorCamera() {
        if (!editorCameraActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity restore = previousCameraEntity;
        if (restore == null || restore.isRemoved()) {
            restore = mc.player;
        }
        if (restore != null) {
            mc.setCameraEntity(restore);
        }
        editorCameraActive = false;
        previousCameraEntity = null;
        editorCameraEntity = null;
    }

    private Vec3 resolveItemTarget() {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(shelfPos);
        Direction facing = resolveFacing(state);
        ItemDisplayTransform transform = currentTransform();
        ShelfDisplayType type = ShelfDisplayType.fromId(shelf == null ? ShelfDisplayType.UNKNOWN.id() : shelf.displayType());
        int resolvedSlot = resolveEditedSlotForCamera();

        return switch (type) {
            case TALL_WALL -> {
                double[] slotY = new double[]{0.74D, 1.22D, 1.66D};
                // Tall shelf editor camera should sit one display tier lower than the selected slot.
                // Clamp keeps the lowest shelf stable instead of underflowing.
                int slot = Mth.clamp(resolvedSlot - 1, 0, slotY.length - 1);
                Vec3 local = new Vec3(0.0D, slotY[slot], 0.18D)
                        .add(transform.offsetX(), transform.offsetY(), transform.offsetZ());
                yield Vec3.atCenterOf(shelfPos).add(rotateByFacing(local, facing));
            }
            case GLASS_COUNTER -> {
                double[] slotY = new double[]{0.38375D, 0.69625D, 1.00875D, 1.32125D};
                int slot = Mth.clamp(resolvedSlot, 0, slotY.length - 1);
                Vec3 local = new Vec3(0.0D, slotY[slot], 0.0D)
                        .add(transform.offsetX(), transform.offsetY(), transform.offsetZ());
                yield Vec3.atCenterOf(shelfPos).add(rotateByFacing(local, facing));
            }
            case MODULAR_WALL -> {
                int visibleSlots = 2;
                var shelfEntity = ShelfService.getDisplayEntity(mc.level, shelfPos);
                if (shelfEntity != null) {
                    visibleSlots = Math.max(1, shelfEntity.getSlotCount());
                }
                int slot = Mth.clamp(resolvedSlot, 0, Math.max(0, visibleSlots - 1));
                boolean fourLayout = visibleSlots > 2;
                int row = fourLayout ? (slot / 2) : slot;
                int col = fourLayout ? (slot % 2) : 0;
                double baseX = fourLayout ? (col == 0 ? -1.00D : 0.00D) : -0.50D;
                double baseY = row <= 0 ? 0.74D : 0.30D;

                // Match the modular renderer transform order exactly:
                // 1) lateral world-width offset, 2) facing-relative row/front offset, 3) facing-relative transform offset.
                Direction right = facing.getClockWise();
                Vec3 lateral = new Vec3(right.getStepX() * baseX, 0.0D, right.getStepZ() * baseX);
                Vec3 rowAndDepth = rotateByFacing(new Vec3(0.0D, baseY - 0.5D, ModularWallDisplayRenderer.ROW_FRONT_Z), facing);
                Vec3 transformedOffset = rotateByFacing(new Vec3(transform.offsetX(), transform.offsetY(), transform.offsetZ()), facing);
                yield Vec3.atCenterOf(shelfPos).add(lateral).add(rowAndDepth).add(transformedOffset);
            }
            case SELLING_TABLE -> {
                boolean large = state != null && (state.is(ModBlocks.SHOP_SELLING_TABLE_LARGE.get())
                        || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get()));
                Vec3 base = large ? new Vec3(1.0D, 1.62D, 0.0D) : new Vec3(0.5D, 1.34D, 0.5D);
                yield Vec3.atLowerCornerOf(shelfPos)
                        .add(base)
                        .add(transform.offsetX(), transform.offsetY(), transform.offsetZ());
            }
            case INVISIBLE_DISPLAY -> Vec3.atLowerCornerOf(shelfPos)
                    .add(0.5D, 0.92D, 0.5D)
                    .add(transform.offsetX(), transform.offsetY(), transform.offsetZ());
            case UNKNOWN -> Vec3.atCenterOf(shelfPos).add(0.0D, 1.0D, 0.0D);
        };
    }

    private int resolveEditedSlotForCamera() {
        int fallback = Math.max(0, absoluteSlotIndex);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shelfPos == null) {
            return fallback;
        }
        var shelfEntity = ShelfService.getDisplayEntity(mc.level, shelfPos);
        if (shelfEntity == null) {
            return fallback;
        }
        // Camera must stay bound to the slot selected in ShelfScreen.
        // Avoid dynamic slot re-selection to prevent gizmo snapping to nearby items.
        int slotCount = Math.max(1, shelfEntity.getSlotCount());
        return Mth.clamp(absoluteSlotIndex, 0, slotCount - 1);
    }

    private double resolveCameraDistance() {
        ShelfDisplayType type = ShelfDisplayType.fromId(shelf == null ? ShelfDisplayType.UNKNOWN.id() : shelf.displayType());
        return switch (type) {
            case TALL_WALL -> 1.08D;
            case GLASS_COUNTER -> 0.92D;
            case MODULAR_WALL -> 1.02D;
            case SELLING_TABLE -> 1.16D;
            case INVISIBLE_DISPLAY -> 1.06D;
            case UNKNOWN -> 1.00D;
        };
    }

    private static Direction resolveFacing(BlockState state) {
        if (state != null && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        return Direction.NORTH;
    }

    private static Vec3 resolveOutwardVector(Direction facing) {
        Vec3 forward = rotateByFacing(new Vec3(0.0D, 0.0D, 1.0D), facing);
        if (forward.lengthSqr() < 1.0E-5D) {
            return new Vec3(0.0D, 0.0D, -1.0D);
        }
        return forward.normalize();
    }

    private static Vec3 rotateByFacing(Vec3 local, Direction facing) {
        if (local == null) {
            return Vec3.ZERO;
        }
        float yaw = facing == null ? 0.0F : -facing.toYRot();
        return local.yRot((float) Math.toRadians(yaw));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 87) { // W
            setMode(ToolMode.MOVE);
            return true;
        }
        if (keyCode == 69) { // E
            setMode(ToolMode.ROTATE);
            return true;
        }
        if (keyCode == 82) { // R
            setMode(ToolMode.SCALE);
            return true;
        }
        if (keyCode == 88) { // X
            setAxis(Axis.X);
            return true;
        }
        if (keyCode == 89) { // Y
            setAxis(Axis.Y);
            return true;
        }
        if (keyCode == 90) { // Z
            setAxis(Axis.Z);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            applyValuesFromFields();
            return true;
        }
        if (keyCode == 256) {
            cancelAndReturn();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Axis clicked = axisAt(mouseX, mouseY);
            if (clicked != null) {
                setAxis(clicked);
                draggingAxis = true;
                lastDragMouseX = mouseX;
                lastDragMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingAxis) {
            draggingAxis = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingAxis) {
            double dx = mouseX - lastDragMouseX;
            double dy = mouseY - lastDragMouseY;
            applyDragDelta(dx, dy);
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void applyDragDelta(double dx, double dy) {
        float fine = Screen.hasShiftDown() ? 0.25F : 1.0F;
        float signedDx = (float) dx;
        float signedDy = (float) dy;
        switch (mode) {
            case MOVE -> {
                float step = 0.0025F * fine;
                if (axis == Axis.X) {
                    offsetX += signedDx * step;
                } else if (axis == Axis.Y) {
                    offsetY -= signedDy * step;
                } else {
                    offsetZ += (signedDx - signedDy) * 0.0018F * fine;
                }
            }
            case ROTATE -> {
                float step = 0.72F * fine;
                if (axis == Axis.X) {
                    rotationX -= signedDy * step;
                } else if (axis == Axis.Y) {
                    rotationY += signedDx * step;
                } else {
                    rotationZ += signedDx * step;
                }
            }
            case SCALE -> {
                float step = 0.004F * fine;
                if (axis == Axis.X) {
                    scaleX += signedDx * step;
                } else if (axis == Axis.Y) {
                    scaleY -= signedDy * step;
                } else {
                    scaleZ += signedDx * step;
                }
            }
        }
        applyTransform(currentTransform(), true);
        updateEditorsFromState(false);
    }

    private Axis axisAt(double mouseX, double mouseY) {
        for (Axis candidate : Axis.values()) {
            int[] point = axisHandlePoint(candidate);
            if (inside(mouseX, mouseY, point[0] - 8, point[1] - 8, 16, 16)) {
                return candidate;
            }
        }
        return null;
    }

    private int[] axisHandlePoint(Axis axis) {
        return switch (axis) {
            case X -> new int[]{gizmoCenterX + gizmoRadius, gizmoCenterY};
            case Y -> new int[]{gizmoCenterX, gizmoCenterY - gizmoRadius};
            case Z -> new int[]{gizmoCenterX - (int) (gizmoRadius * 0.74F), gizmoCenterY + (int) (gizmoRadius * 0.74F)};
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible for real-time placement preview.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bottom = panelTop + panelHeight;
        String title = "Shelf Transform Editor";
        String subtitle = "Display: " + prettyDisplayType(shelf == null ? ShelfDisplayType.UNKNOWN.id() : shelf.displayType())
                + " | Slot " + (slotIndex + 1);
        drawTextTag(graphics, title, panelLeft + 12, panelTop + 11, 0xFFF5FCFF, 0xA0101720);
        drawTextTag(graphics, subtitle, panelLeft + 12, panelTop + 27, 0xFFD6EBFF, 0xA0101720);

        drawGizmo(graphics, mouseX, mouseY);
        drawRightPanelText(graphics);

        if (!feedbackMessage.isBlank()) {
            int color = feedbackSuccess ? 0xFF8FFFB4 : 0xFFFFA2A2;
            drawTextTag(graphics, fitText(feedbackMessage, panelWidth - 24), panelLeft + 12, bottom - 40, color, 0x9E161D24);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        Axis hovered = axisAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(font, UbsTranslations.literal(hoverTooltip(hovered)), mouseX, mouseY);
        }
    }

    private void drawRightPanelText(GuiGraphics graphics) {
        // Start helper text above the value fields, shifted right so it doesn't overlap axis controls.
        int x = valueFieldStartX;
        int y = panelTop + 44;
        drawTextTag(graphics, "Manual Values", x, y, 0xFFEAF5FF, 0xA0101720);
        drawTextTag(graphics, "Shift = fine control", x, y + 12, 0xFFB7CEE6, 0xA0101720);

        int labelX = valueLabelX;
        for (int i = 0; i < valueLabels.length; i++) {
            graphics.drawString(font, tr(valueLabels[i]), labelX, fieldY(i) + 4, 0xFFE1EEFF, true);
        }
    }

    private void drawGizmo(GuiGraphics graphics, int mouseX, int mouseY) {
        int cx = gizmoCenterX;
        int cy = gizmoCenterY;

        drawCircle(graphics, cx, cy, gizmoRadius + 12, 0x44D7EAFF);
        drawCircle(graphics, cx, cy, gizmoRadius + 4, 0x55EAF5FF);
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        drawAxis(graphics, Axis.X);
        drawAxis(graphics, Axis.Y);
        drawAxis(graphics, Axis.Z);

        int helperY = Math.max(panelTop + 70, cy - gizmoRadius - 24);
        drawCenteredTextTag(graphics, mode.label, cx, helperY - 14, 0xFFEAF5FF, 0xB0182430);
        drawCenteredTextTag(graphics, "W/E/R = mode  |  X/Y/Z = axis", cx, helperY - 1, 0xFFBCD4ED, 0xB0182430);
        drawCenteredTextTag(graphics, "Drag an axis handle or use manual values", cx, helperY + 12, 0xFF95B8DA, 0xB0182430);

        String live = switch (mode) {
            case MOVE -> "Δ " + axis.name() + "  " + formatShort(getAxisValue(mode, axis));
            case ROTATE -> "° " + axis.name() + "  " + formatShort(getAxisValue(mode, axis));
            case SCALE -> "× " + axis.name() + "  " + formatShort(getAxisValue(mode, axis));
        };
        graphics.drawCenteredString(font, live, cx, cy + gizmoRadius + 12, 0xFFF7E89D);
    }

    private void drawAxis(GuiGraphics graphics, Axis axisType) {
        int[] handle = axisHandlePoint(axisType);
        int color = axisColor(axisType, axis == axisType);

        if (axisType == Axis.X) {
            graphics.fill(gizmoCenterX, gizmoCenterY - 1, handle[0], gizmoCenterY + 1, color);
        } else if (axisType == Axis.Y) {
            graphics.fill(gizmoCenterX - 1, handle[1], gizmoCenterX + 1, gizmoCenterY, color);
        } else {
            drawDiagonalLine(graphics, gizmoCenterX, gizmoCenterY, handle[0], handle[1], color);
        }

        int edgeColor = 0xFFF5FCFF;
        graphics.fill(handle[0] - 5, handle[1] - 5, handle[0] + 5, handle[1] + 5, edgeColor);
        graphics.fill(handle[0] - 4, handle[1] - 4, handle[0] + 4, handle[1] + 4, color);
    }

    private static void drawDiagonalLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (radius <= 1) {
            return;
        }
        int x = radius;
        int y = 0;
        int err = 0;
        while (x >= y) {
            plotCirclePoints(graphics, cx, cy, x, y, color);
            y++;
            if (err <= 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void plotCirclePoints(GuiGraphics graphics, int cx, int cy, int x, int y, int color) {
        graphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
        graphics.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
        graphics.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
        graphics.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
        graphics.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
        graphics.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
        graphics.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
        graphics.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
    }

    private float getAxisValue(ToolMode mode, Axis axis) {
        return switch (mode) {
            case MOVE -> axis == Axis.X ? offsetX : axis == Axis.Y ? offsetY : offsetZ;
            case ROTATE -> axis == Axis.X ? rotationX : axis == Axis.Y ? rotationY : rotationZ;
            case SCALE -> axis == Axis.X ? scaleX : axis == Axis.Y ? scaleY : scaleZ;
        };
    }

    private static String hoverTooltip(Axis axis) {
        return switch (axis) {
            case X -> "X Axis";
            case Y -> "Y Axis";
            case Z -> "Z Axis";
        };
    }

    private static String prettyDisplayType(String typeId) {
        return switch (ShelfDisplayType.fromId(typeId)) {
            case TALL_WALL -> "Wall Shelf";
            case GLASS_COUNTER -> "Glass Counter";
            case MODULAR_WALL -> "Modular Wall";
            case SELLING_TABLE -> "Display Table";
            case INVISIBLE_DISPLAY -> "Invisible Display";
            case UNKNOWN -> "Shelf Display";
        };
    }

    private String fitText(String text, int maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return "";
        }
        text = tr(text);
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static String tr(String text) {
        return UbsClientTranslations.resolve(text == null ? "" : text);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String formatShort(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static float round3(float value) {
        return Math.round(value * 1000.0F) / 1000.0F;
    }

    private void drawTextTag(GuiGraphics graphics, String text, int x, int y, int textColor, int backgroundColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        text = tr(text);
        int width = font.width(text);
        graphics.fill(x - 3, y - 2, x + width + 3, y + 9, backgroundColor);
        graphics.drawString(font, text, x, y, textColor, false);
    }

    private void drawCenteredTextTag(GuiGraphics graphics, String text, int centerX, int y, int textColor, int backgroundColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        int width = font.width(text);
        int x = centerX - width / 2;
        drawTextTag(graphics, text, x, y, textColor, backgroundColor);
    }

    @Override
    public void removed() {
        if (spectatorModeRequested) {
            syncSpectatorMode(false);
            spectatorModeRequested = false;
        }
        super.removed();
        restoreEditorCamera();
        if (!saveRequested) {
            clearPreview();
        }
    }

    private static ParsedShelfTarget parseShelfTarget(String key) {
        if (key == null || key.isBlank()) {
            return new ParsedShelfTarget(null, -1);
        }
        String[] parts = key.trim().split("\\|", 2);
        String[] coords = parts[0].split(",");
        if (coords.length != 3) {
            return new ParsedShelfTarget(null, -1);
        }
        try {
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            int z = Integer.parseInt(coords[2].trim());
            int row = -1;
            if (parts.length == 2 && parts[1].startsWith("r")) {
                try {
                    row = Math.max(0, Integer.parseInt(parts[1].substring(1)));
                } catch (NumberFormatException ignored) {
                    row = -1;
                }
            }
            return new ParsedShelfTarget(new BlockPos(x, y, z), row);
        } catch (NumberFormatException ex) {
            return new ParsedShelfTarget(null, -1);
        }
    }

    private void syncSpectatorMode(boolean enable) {
        if (payload == null || shelf == null) {
            return;
        }
        PacketDistributor.sendToServer(new ShelfActionPayload(
                payload.dimensionId(),
                payload.rootX(),
                payload.rootY(),
                payload.rootZ(),
                shelf.posKey(),
                "positioner_mode",
                slotIndex,
                enable ? "enter" : "exit",
                -1
        ));
    }

    private enum ToolMode {
        MOVE("Move"),
        ROTATE("Rotate"),
        SCALE("Scale");

        private final String label;

        ToolMode(String label) {
            this.label = label;
        }
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private record ParsedShelfTarget(BlockPos pos, int row) {
    }
}
