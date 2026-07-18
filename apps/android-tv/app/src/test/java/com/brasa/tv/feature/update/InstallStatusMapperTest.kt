package com.brasa.tv.feature.update
import android.content.pm.PackageInstaller
import org.junit.Assert.assertTrue
import org.junit.Test
class InstallStatusMapperTest{@Test fun mapsCancellationBlockingConflictCompatibilityStorageAndInvalid(){val statuses=listOf(PackageInstaller.STATUS_FAILURE_ABORTED,PackageInstaller.STATUS_FAILURE_BLOCKED,PackageInstaller.STATUS_FAILURE_CONFLICT,PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,PackageInstaller.STATUS_FAILURE_STORAGE,PackageInstaller.STATUS_FAILURE_INVALID);statuses.forEach{assertTrue(InstallStatusMapper.message(InstallResult(it)).isNotBlank())}}@Test fun preservesSafeSystemDetailForGenericFailure(){assertTrue(InstallStatusMapper.message(InstallResult(PackageInstaller.STATUS_FAILURE,"Falha controlada")).contains("controlada"))}}
