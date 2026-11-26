package dev.schematicstock.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.schematicstock.PlacementMaterialLoad;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.scheduler.ITask;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.materials.MaterialListPlacement")
abstract class MaterialListPlacementMixin implements PlacementMaterialLoad {
    @Unique private ITask tomsmatic$materialTask;
    @Override
    public void refreshMaterials() {
        if (!materialsLoading()) ((MaterialListPlacement) (Object) this).reCreateMaterialList();
    }

    @Override
    public boolean materialsLoading() {
        return tomsmatic$materialTask != null;
    }

    @WrapOperation(method = "reCreateMaterialList", at = @At(value = "INVOKE", target =
        "Lfi/dy/masa/litematica/scheduler/TaskScheduler;scheduleTask(Lfi/dy/masa/litematica/scheduler/ITask;I)V"))
    private void tomsmatic$trackMaterialTask(TaskScheduler scheduler, ITask task, int interval, Operation<Void> original) {
        tomsmatic$materialTask = task;
        ((TaskCountBlocksPlacement) task).setCompletionListener(new ICompletionListener() {
            @Override
            public void onTaskCompleted() {
                if (tomsmatic$materialTask == task) tomsmatic$materialTask = null;
            }

            @Override
            public void onTaskAborted() {
                onTaskCompleted();
            }
        });
        original.call(scheduler, task, interval);
    }
}
