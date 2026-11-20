#!/usr/bin/env python3
"""Relocate the embedded slf4j-api into a private package and rewrite the references to it.

The measured failure this fixes: jcpp's Preprocessor static-initialises an slf4j Logger. FML's
LaunchClassLoader excludes org.slf4j from the transformable class path, and nothing on this 1.10.2
instance provides slf4j at runtime, so loading any shaderpack dies with

    java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory

inside ShaderPack loading, which kills the client on the first rendered frame.

The fix keeps FML's exclusion intact (other mods on the instance may load their own slf4j) by moving
the API into a package only this jar uses:

    org/slf4j/...        ->  com/dhj/actinium/deps/slf4j/...

Every class that references org/slf4j in its constant pool is rewritten through ASM's
Remapper, which covers class names, descriptors and signatures in one pass. slf4j-api has no
binding of its own; the relocated copy falls back to the NOP logger slf4j emits when no provider
is present, which is the intended behaviour for a library that only logs diagnostics.

Usage: relocate_slf4j.py <slf4j-api-jar> <stage-dir> <asm-jar> <asm-commons-jar> <asm-tree-jar>
"""

import os
import subprocess
import sys
import zipfile

DRIVER = r"""
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class RelocateSlf4j {

    private static final String FROM = "org/slf4j/";
    private static final String TO = "com/dhj/actinium/deps/slf4j/";

    public static void main(String[] args) throws Exception {
        final Path jar = Paths.get(args[0]);
        final Path stage = Paths.get(args[1]);
        final Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName.startsWith(FROM)) {
                    return TO + internalName.substring(FROM.length());
                }
                return internalName;
            }
        };

        int relocated = 0;
        int rewritten = 0;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            final java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final java.util.zip.ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (name.endsWith(".class") && name.startsWith(FROM)) {
                    final byte[] original = zip.getInputStream(entry).readAllBytes();
                    final ClassReader reader = new ClassReader(original);
                    final ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    final byte[] updated = writer.toByteArray();
                    final String out = TO + name.substring(FROM.length());
                    final Path target = stage.resolve(out);
                    Files.createDirectories(target.getParent());
                    Files.write(target, updated);
                    relocated++;
                }
            }
        }

        // Rewrite every staged class whose constant pool still references org/slf4j.
        final List<Path> files = new ArrayList<>();
        Files.walk(stage).filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        final byte[] needle = "org/slf4j".getBytes(StandardCharsets.US_ASCII);
        for (Path file : files) {
            final byte[] original = Files.readAllBytes(file);
            if (!contains(original, needle)) {
                continue;
            }
            final ClassReader reader = new ClassReader(original);
            final ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, remapper), 0);
            final Path temporary = file.resolveSibling(file.getFileName() + ".new");
            Files.write(temporary, writer.toByteArray());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            rewritten++;
        }
        System.out.println("relocated " + relocated + " slf4j class(es), rewrote "
            + rewritten + " referring class(es)");
    }

    private static boolean contains(byte[] data, byte[] needle) {
        outer: for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
"""


def main() -> int:
    slf4j_jar, stage, asm, asm_commons, asm_tree = (
        sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    driver = os.path.join(os.path.dirname(stage), 'scratch')
    os.makedirs(driver, exist_ok=True)
    driver = os.path.join(driver, 'RelocateSlf4j.java')
    with open(driver, 'w', encoding='utf-8') as handle:
        handle.write(DRIVER)
    cls = driver[:-5] + '.class'
    if os.path.exists(cls):
        os.remove(cls)
    compile_cmd = ['javac', '-cp', ';'.join([asm, asm_commons, asm_tree]), driver]
    result = subprocess.run(compile_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print('javac failed: ' + result.stderr)
        return 1
    run_cmd = ['java', '-cp', ';'.join([os.path.dirname(driver), asm, asm_commons, asm_tree]),
               'RelocateSlf4j', slf4j_jar, stage]
    result = subprocess.run(run_cmd, capture_output=True, text=True)
    print(result.stdout.strip())
    if result.returncode != 0:
        print(result.stderr)
        return 1
    os.remove(driver)
    if os.path.exists(cls):
        os.remove(cls)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
