package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.kronwerke.core.share.mek.QioDrives;
import mekanism.common.content.qio.QIODriveData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where a QIO frequency makes the data of a drive it was given: for the two drives Core
 * gives it (see {@link QioDrives}) the data is Core's own kind.
 */
@Pseudo
@Mixin(targets = "mekanism.common.content.qio.QIOFrequency", remap = false)
public abstract class QioDriveNewMixin {
    @WrapOperation(method = "addDrive", require = 0, at = @At(value = "NEW",
            target = "(Lmekanism/common/content/qio/QIODriveData$QIODriveKey;)Lmekanism/common/content/qio/QIODriveData;"))
    private QIODriveData kronwerke$ownDrives(QIODriveData.QIODriveKey key, Operation<QIODriveData> original) {
        QIODriveData own = QioDrives.create(key);
        return own != null ? own : original.call(key);
    }
}
