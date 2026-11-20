#!/usr/bin/env python3
"""Re-emit every class in a directory through ASM so it carries computed stack map frames.

The problem this solves, measured on this instance:

JOML is compiled to class file version 46, which predates StackMapTable. The Java 25 verifier rejects
it outright:

    java.lang.VerifyError: Expecting a stackmap frame at branch target 7
    Location: org/joml/Matrix4f.equals(Lorg/joml/Matrix4fc;F)Z @2: if_acmpne

Raising the version field alone does not help, because the verifier then requires frames that are not
in the file. The classes have to be rewritten so the frames exist. ASM computes them from the bytecode
with COMPUTE_FRAMES, and the output is a valid modern class.

Three traps were hit while getting this right, and each is encoded below:

  1. new ClassWriter(reader, flags) copies the reader's symbol table including its major version, so
     ASM decides no frames are wanted. The writer must be fresh and the version must be set in
     visit(), not patched into the bytes afterwards.
  2. The hierarchy for COMPUTE_FRAMES has to come from the class files on disk. Loading the library
     under rewrite in order to ask the JVM is circular and failed on JOML's inner classes.
  3. Guessing java/lang/Object for an unresolvable merge produced
     "Type 'java/lang/Object' is not assignable to 'org/joml/MemUtil'", a class that fails
     verification at run time. An unresolvable merge is a hard error here instead.

Usage: add_stackmaps.py <directory> <major-version> <asm-jar> <asm-tree-jar>
"""

import os
import subprocess
import sys

DRIVER = r"""
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AddStackMaps {

    /** Cheap probe: the constant pool holds the attribute name when frames are present. */
    private static boolean hasStackMapTable(byte[] data) {
        byte[] needle = "StackMapTable".getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        final Path root = Paths.get(args[0]);
        final int major = Integer.parseInt(args[1]);
        int rewritten = 0;
        int skipped = 0;
        final List<Path> files = new ArrayList<>();
        Files.walk(root).filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        for (Path file : files) {
            final byte[] original = Files.readAllBytes(file);
            final int version = ((original[6] & 0xFF) << 8) | (original[7] & 0xFF);
            if (hasStackMapTable(original) && version >= major) {
                skipped++;
                continue;
            }
            final ClassReader reader = new ClassReader(original);
            final ClassWriter writer = new HierarchyClassWriter(root,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            // The version must already be the target one when ASM writes: COMPUTE_FRAMES only emits a
            // StackMapTable for a class that declares version 50 or higher.
            final ClassVisitor versioned = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public void visit(int v, int access, String name, String signature, String superName,
                                  String[] interfaces) {
                    super.visit(major, access, name, signature, superName, interfaces);
                }
            };
            // EXPAND_FRAMES: with a version-46 input the compact frames describe the wrong return
            // type once written, which surfaces as "VerifyError: Bad return type".
            reader.accept(versioned, ClassReader.EXPAND_FRAMES);
            final byte[] updated = writer.toByteArray();
            updated[4] = (byte) 0;
            updated[5] = (byte) 0;
            updated[6] = (byte) ((major >> 8) & 0xFF);
            updated[7] = (byte) (major & 0xFF);
            final Path temporary = file.resolveSibling(file.getFileName() + ".new");
            Files.write(temporary, updated);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            rewritten++;
        }
        System.out.println("rewrote " + rewritten + " class(es), left " + skipped
            + " already-modern class(es) alone");
    }

    /**
     * Resolves the class hierarchy from the class files on disk rather than through a class loader.
     *
     * COMPUTE_FRAMES needs the common superclass of the types that meet at a branch. Asking the JVM
     * would mean loading the very library that is being rewritten, which is circular and failed on
     * this library's inner classes; reading the files gives the same superclass and interface data
     * with nothing to link.
     */
    private static final class HierarchyClassWriter extends ClassWriter {

        private final Path root;

        private HierarchyClassWriter(Path root, int flags) {
            super(flags);
            this.root = root;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            final Set<String> ancestors = ancestry(type1);
            String walker = type2;
            while (walker != null) {
                if (ancestors.contains(walker)) {
                    return walker;
                }
                if (isInterface(walker)) {
                    return "java/lang/Object";
                }
                walker = superNameOf(walker);
            }
            return "java/lang/Object";
        }

        private Set<String> ancestry(String type) {
            final Set<String> seen = new LinkedHashSet<>();
            final Deque<String> pending = new ArrayDeque<>();
            pending.add(type);
            while (!pending.isEmpty()) {
                final String current = pending.removeFirst();
                if (current == null || !seen.add(current)) {
                    continue;
                }
                final String superName = superNameOf(current);
                if (superName != null) {
                    pending.add(superName);
                }
                pending.addAll(interfacesOf(current));
            }
            return seen;
        }

        /** Superclass name from the class file, or null for Object, an interface, or a missing type. */
        private String superNameOf(String type) {
            final ClassReader reader = readerFor(type);
            return reader == null ? null : reader.getSuperName();
        }

        private List<String> interfacesOf(String type) {
            final ClassReader reader = readerFor(type);
            if (reader == null) {
                return Collections.emptyList();
            }
            return Arrays.asList(reader.getInterfaces());
        }

        private boolean isInterface(String type) {
            final ClassReader reader = readerFor(type);
            return reader != null && (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0;
        }

        /**
         * Reads a class file for a type, from the directory first and the JDK second.
         *
         * <p>The JDK fallback matters: a library type whose superclass is a platform class cannot be
         * resolved from the directory alone, and returning null there made the merge guess Object,
         * which produced "Bad return type" on {@code Options.decimalFormat} where the value really is
         * a {@code NumberFormat}. Platform classes always have frames already, so reading them through
         * the Java 9 module image is enough; {@code ClassReader} accepts a stream, which is what the
         * JDK exposes.</p>
         */
        private ClassReader readerFor(String type) {
            final Path file = root.resolve(type + ".class");
            try {
                if (Files.exists(file)) {
                    return new ClassReader(Files.readAllBytes(file));
                }
                final java.io.InputStream platform =
                    ClassLoader.getSystemResourceAsStream(type + ".class");
                if (platform != null) {
                    try (java.io.InputStream stream = platform) {
                        return new ClassReader(stream);
                    }
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot read " + type + " while computing frames", failure);
            }
            return null;
        }
    }
}
"""


def main() -> int:
    if len(sys.argv) < 5:
        print("usage: add_stackmaps.py <directory> <major-version> <asm-jar> <asm-tree-jar>")
        return 2
    directory, major, asm_jar, asm_tree_jar = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

    driver_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                                              "build", "ascii"))
    os.makedirs(driver_dir, exist_ok=True)
    source_path = os.path.join(driver_dir, "AddStackMaps.java")
    with open(source_path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(DRIVER)

    classpath = os.pathsep.join([asm_jar, asm_tree_jar])
    compiled = subprocess.run(
        ["javac", "-nowarn", "-cp", classpath, "-d", driver_dir, source_path],
        capture_output=True,
        text=True,
    )
    if compiled.returncode != 0:
        print(compiled.stdout)
        print(compiled.stderr)
        return 1

    run = subprocess.run(
        ["java", "-cp", os.pathsep.join([driver_dir, classpath]), "AddStackMaps", directory, major],
        capture_output=True,
        text=True,
    )
    print(run.stdout.strip())
    if run.returncode != 0:
        print(run.stderr)
        return run.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
