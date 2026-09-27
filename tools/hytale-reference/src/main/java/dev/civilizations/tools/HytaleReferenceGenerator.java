package dev.civilizations.tools;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Generates a deterministic, source-free reference catalog from the exact Hytale Server
 * artifact resolved by Gradle.
 */
public final class HytaleReferenceGenerator {

    private HytaleReferenceGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);

        Path serverArtifact = requiredPath(options, "server-artifact");
        Path output = requiredPath(options, "output");
        String classpath = required(options, "classpath");
        String requestedVersion = required(options, "requested-version");
        String resolvedCoordinate = required(options, "resolved-coordinate");

        Files.createDirectories(output);

        ScanResult scan = scanJar(serverArtifact);
        ApiResult api = inspectApi(scan.classNames(), classpath);

        write(output.resolve("manifest.txt"), manifest(
            requestedVersion,
            resolvedCoordinate,
            serverArtifact,
            scan,
            api
        ));
        write(output.resolve("packages.txt"), packageIndex(scan.classNames()));
        write(output.resolve("classes.txt"), classIndex(scan.classNames()));
        write(output.resolve("api.txt"), api.lines());
        write(output.resolve("blocks.txt"), assetIndex(
            "Block IDs derived from JSON resources with a BlockType field in the resolved Server artifact.",
            scan.blockIds()
        ));
        write(output.resolve("items.txt"), assetIndex(
            "Item IDs derived from Server/Item/Items JSON resources in the resolved Server artifact.",
            scan.itemIds()
        ));
        write(output.resolve("prefabs.txt"), assetIndex(
            "Prefab paths derived from Server/Prefabs resources in the resolved Server artifact.",
            scan.prefabPaths()
        ));
        write(output.resolve("npc-roles.txt"), assetIndex(
            "NPC role IDs derived from Server/NPC/Roles resources in the resolved Server artifact.",
            scan.npcRoleIds()
        ));
    }

    private static ScanResult scanJar(Path artifact) throws IOException {
        Set<String> classNames = new TreeSet<>();
        Set<String> blockIds = new TreeSet<>();
        Set<String> itemIds = new TreeSet<>();
        Set<String> prefabPaths = new TreeSet<>();
        Set<String> npcRoleIds = new TreeSet<>();

        try (JarFile jar = new JarFile(artifact.toFile())) {
            List<JarEntry> entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .sorted(Comparator.comparing(JarEntry::getName))
                .toList();

            for (JarEntry entry : entries) {
                String name = entry.getName();

                if (isHytaleClass(name)) {
                    classNames.add(toClassName(name));
                    continue;
                }

                if (name.startsWith("Server/Prefabs/") && name.endsWith(".prefab.json")) {
                    prefabPaths.add(stripPrefixAndSuffix(name, "Server/Prefabs/", ".prefab.json"));
                    continue;
                }

                if (name.startsWith("Server/NPC/Roles/") && name.endsWith(".json")) {
                    npcRoleIds.add(stripPrefixAndSuffix(name, "Server/NPC/Roles/", ".json"));
                    continue;
                }

                if (name.startsWith("Server/Item/Items/") && name.endsWith(".json")) {
                    String id = fileStem(name);
                    itemIds.add(id);

                    try (InputStream stream = jar.getInputStream(entry)) {
                        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        if (text.contains("\"BlockType\"")) {
                            blockIds.add(id);
                        }
                    }
                }
            }
        }

        return new ScanResult(classNames, blockIds, itemIds, prefabPaths, npcRoleIds);
    }

    private static ApiResult inspectApi(Set<String> classNames, String classpath) throws IOException {
        URL[] urls = Arrays.stream(classpath.split(java.io.File.pathSeparator))
            .filter(value -> !value.isBlank())
            .map(Path::of)
            .map(Path::toUri)
            .map(uri -> {
                try {
                    return uri.toURL();
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Invalid classpath entry: " + uri, exception);
                }
            })
            .toArray(URL[]::new);

        List<String> lines = new ArrayList<>();
        int inspected = 0;
        int unavailable = 0;

        lines.add("# Public/protected declared API from the resolved Hytale Server artifact.");
        lines.add("# Signatures are reflection-derived metadata only; no Hytale source code is included.");
        lines.add("");

        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            for (String className : classNames) {
                if (className.endsWith(".package-info") || className.equals("module-info")) {
                    continue;
                }

                try {
                    Class<?> type = Class.forName(className, false, loader);
                    if (!Modifier.isPublic(type.getModifiers()) && !Modifier.isProtected(type.getModifiers())) {
                        continue;
                    }

                    inspected++;
                    lines.add(typeHeader(type));

                    List<String> members = new ArrayList<>();
                    for (Field field : safeFields(type)) {
                        if (isReferenceMember(field.getModifiers()) && !field.isSynthetic()) {
                            members.add("  FIELD " + modifiers(field.getModifiers())
                                + typeName(field.getType()) + " " + field.getName());
                        }
                    }

                    for (Constructor<?> constructor : safeConstructors(type)) {
                        if (isReferenceMember(constructor.getModifiers()) && !constructor.isSynthetic()) {
                            members.add("  CTOR " + modifiers(constructor.getModifiers())
                                + simpleName(type) + parameterList(constructor.getParameterTypes())
                                + throwsList(constructor.getExceptionTypes()));
                        }
                    }

                    for (Method method : safeMethods(type)) {
                        if (isReferenceMember(method.getModifiers())
                            && !method.isSynthetic()
                            && !method.isBridge()) {
                            members.add("  METHOD " + modifiers(method.getModifiers())
                                + typeName(method.getReturnType()) + " " + method.getName()
                                + parameterList(method.getParameterTypes())
                                + throwsList(method.getExceptionTypes()));
                        }
                    }

                    members.stream().sorted().forEach(lines::add);
                    lines.add("");
                } catch (Throwable error) {
                    unavailable++;
                    lines.add("UNAVAILABLE " + className + " [" + error.getClass().getName() + "]");
                    lines.add("");
                }
            }
        }

        return new ApiResult(lines, inspected, unavailable);
    }

    private static Field[] safeFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable ignored) {
            return new Field[0];
        }
    }

    private static Constructor<?>[] safeConstructors(Class<?> type) {
        try {
            return type.getDeclaredConstructors();
        } catch (Throwable ignored) {
            return new Constructor<?>[0];
        }
    }

    private static Method[] safeMethods(Class<?> type) {
        try {
            return type.getDeclaredMethods();
        } catch (Throwable ignored) {
            return new Method[0];
        }
    }

    private static String typeHeader(Class<?> type) {
        String kind;
        if (type.isAnnotation()) {
            kind = "ANNOTATION";
        } else if (type.isEnum()) {
            kind = "ENUM";
        } else if (type.isInterface()) {
            kind = "INTERFACE";
        } else if (type.isRecord()) {
            kind = "RECORD";
        } else {
            kind = "CLASS";
        }

        StringBuilder line = new StringBuilder(kind)
            .append(' ')
            .append(type.getName());

        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class && !type.isEnum()) {
            line.append(" EXTENDS ").append(typeName(superclass));
        }

        Class<?>[] interfaces = type.getInterfaces();
        if (interfaces.length > 0) {
            line.append(" IMPLEMENTS ");
            line.append(Arrays.stream(interfaces)
                .map(HytaleReferenceGenerator::typeName)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(""));
        }

        return line.toString();
    }

    private static String modifiers(int value) {
        List<String> parts = new ArrayList<>();
        if (Modifier.isPublic(value)) parts.add("public");
        if (Modifier.isProtected(value)) parts.add("protected");
        if (Modifier.isStatic(value)) parts.add("static");
        if (Modifier.isFinal(value)) parts.add("final");
        if (Modifier.isAbstract(value)) parts.add("abstract");
        if (Modifier.isSynchronized(value)) parts.add("synchronized");
        if (Modifier.isNative(value)) parts.add("native");
        return parts.isEmpty() ? "" : String.join(" ", parts) + " ";
    }

    private static boolean isReferenceMember(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String parameterList(Class<?>[] types) {
        return "(" + Arrays.stream(types)
            .map(HytaleReferenceGenerator::typeName)
            .reduce((left, right) -> left + ", " + right)
            .orElse("") + ")";
    }

    private static String throwsList(Class<?>[] types) {
        if (types.length == 0) {
            return "";
        }
        return " throws " + Arrays.stream(types)
            .map(HytaleReferenceGenerator::typeName)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private static String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        return type.getTypeName();
    }

    private static String simpleName(Class<?> type) {
        String simple = type.getSimpleName();
        return simple.isBlank() ? type.getName() : simple;
    }

    private static List<String> packageIndex(Set<String> classNames) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String className : classNames) {
            int split = className.lastIndexOf('.');
            String packageName = split < 0 ? "(default)" : className.substring(0, split);
            counts.merge(packageName, 1, Integer::sum);
        }

        List<String> lines = new ArrayList<>();
        lines.add("# Package index from the resolved Hytale Server artifact.");
        lines.add("");
        counts.forEach((name, count) -> lines.add(name + " " + count));
        return lines;
    }

    private static List<String> classIndex(Set<String> classNames) {
        List<String> lines = new ArrayList<>();
        lines.add("# Class names from the resolved Hytale Server artifact.");
        lines.add("# Includes nested classes because they are often relevant to codecs, events and builders.");
        lines.add("");
        lines.addAll(classNames);
        return lines;
    }

    private static List<String> assetIndex(String description, Set<String> values) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + description);
        lines.add("# If this section is empty, the Maven Server artifact does not expose that asset catalog.");
        lines.add("");
        lines.addAll(values);
        return lines;
    }

    private static List<String> manifest(
        String requestedVersion,
        String resolvedCoordinate,
        Path artifact,
        ScanResult scan,
        ApiResult api
    ) throws IOException, NoSuchAlgorithmException {
        List<String> lines = new ArrayList<>();
        lines.add("format=1");
        lines.add("requestedVersion=" + requestedVersion);
        lines.add("resolvedCoordinate=" + resolvedCoordinate);
        lines.add("serverArtifactSha256=" + sha256(artifact));
        lines.add("classCount=" + scan.classNames().size());
        lines.add("publicApiClassCount=" + api.inspected());
        lines.add("unavailableApiClassCount=" + api.unavailable());
        lines.add("blockIdCount=" + scan.blockIds().size());
        lines.add("itemIdCount=" + scan.itemIds().size());
        lines.add("prefabPathCount=" + scan.prefabPaths().size());
        lines.add("npcRoleIdCount=" + scan.npcRoleIds().size());
        return lines;
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return hex.toString();
    }

    private static boolean isHytaleClass(String entry) {
        return entry.startsWith("com/hypixel/hytale/")
            && entry.endsWith(".class")
            && !entry.startsWith("META-INF/versions/");
    }

    private static String toClassName(String entry) {
        return entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
    }

    private static String stripPrefixAndSuffix(String value, String prefix, String suffix) {
        return value.substring(prefix.length(), value.length() - suffix.length());
    }

    private static String fileStem(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return file.substring(0, file.length() - ".json".length());
    }

    private static void write(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseArgs(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be --key value pairs.");
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String key = args[index];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Expected --key but got: " + key);
            }
            values.put(key.substring(2), args[index + 1]);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing --" + key);
        }
        return value;
    }

    private static Path requiredPath(Map<String, String> values, String key) {
        return Path.of(required(values, key));
    }

    private record ScanResult(
        Set<String> classNames,
        Set<String> blockIds,
        Set<String> itemIds,
        Set<String> prefabPaths,
        Set<String> npcRoleIds
    ) {
    }

    private record ApiResult(List<String> lines, int inspected, int unavailable) {
    }
}
