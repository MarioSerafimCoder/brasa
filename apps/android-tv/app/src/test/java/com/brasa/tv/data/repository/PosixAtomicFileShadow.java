package com.brasa.tv.data.repository;

import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Only bridge rename: Windows File.renameTo cannot replace files like Android/Linux does.
 * Keep the real Android journal, serialization, writes, sync, reads and recovery code. */
@Implements(AtomicFile.class)
public class PosixAtomicFileShadow {
    @Implementation
    protected static void rename(File source, File target) throws IOException {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
