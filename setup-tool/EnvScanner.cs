using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;

namespace ProtactiniumSetup;

/** 单项检测状态。 */
public enum CheckState
{
    Ok,        // 已就绪
    Missing,   // 缺失，必须补全
    Optional,  // 缺失，但不阻塞
}

public sealed record CheckItem(
    string Group,
    string Name,
    string Why,
    CheckState State,
    string Detail,
    string? ManualHint = null,
    string? AutoFixUrl = null,
    string? AutoFixTarget = null);

public static class EnvScanner
{
    public static string RepoRoot { get; private set; } = "";
    public static string GameDir { get; private set; } = "";

    public static List<CheckItem> ScanAll()
    {
        LoadPaths();
        var items = new List<CheckItem>();
        items.AddRange(ScanBuild());
        items.AddRange(ScanRuntime());
        return items;
    }

    public static void LoadPaths()
    {
        var exeDir = AppContext.BaseDirectory;
        var guess = exeDir;
        for (int i = 0; i < 6 && !string.IsNullOrEmpty(guess); i++)
        {
            if (File.Exists(Path.Combine(guess, "build.ps1")))
            {
                RepoRoot = guess;
                break;
            }
            guess = Path.GetDirectoryName(guess) ?? "";
        }
        if (RepoRoot.Length == 0)
        {
            RepoRoot = exeDir;
        }

        var configPath = Path.Combine(exeDir, "setup-paths.json");
        if (File.Exists(configPath))
        {
            try
            {
                var json = File.ReadAllText(configPath);
                using var doc = System.Text.Json.JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("Repo", out var repo) && repo.GetString() is { Length: > 0 } r)
                {
                    RepoRoot = r;
                }
                if (doc.RootElement.TryGetProperty("Game", out var game) && game.GetString() is { Length: > 0 } g)
                {
                    GameDir = g;
                    return;
                }
            }
            catch { }
        }

        GameDir = FindGameDir();
    }

    private static string FindGameDir()
    {
        var candidates = new List<string>();
        var pclVersions = @"D:pclersions";
        if (Directory.Exists(pclVersions))
        {
            candidates.AddRange(Directory.GetDirectories(pclVersions));
        }
        foreach (var dir in candidates)
        {
            var mods = Path.Combine(dir, "mods");
            if (Directory.Exists(mods) && Directory.GetFiles(mods, "Protactinium*.jar").Length > 0)
            {
                return dir;
            }
        }
        return candidates.FirstOrDefault() ?? "";
    }

    private static string RunCapture(string fileName, string arguments)
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = fileName,
                Arguments = arguments,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            using var p = Process.Start(psi)!;
            var output = p.StandardOutput.ReadToEnd() + p.StandardError.ReadToEnd();
            p.WaitForExit(15000);
            return output.Trim();
        }
        catch
        {
            return "";
        }
    }

    private static int JavaMajorVersion(string javaExe)
    {
        var outp = RunCapture(javaExe, "-version");
        var idx = outp.IndexOf("version \"", StringComparison.Ordinal);
        if (idx < 0) return 0;
        var rest = outp[(idx + 9)..];
        var quote = rest.IndexOf('"');
        if (quote < 0) return 0;
        var ver = rest[..quote];
        var first = ver.Split('.')[0].Split('-')[0];
        return int.TryParse(first, out var major) ? major : 0;
    }

    private static string FindOnPath(string tool)
    {
        var pathEnv = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (var dir in pathEnv.Split(';', StringSplitOptions.RemoveEmptyEntries))
        {
            try
            {
                var full = Path.Combine(dir.Trim(), tool);
                if (File.Exists(full)) return full;
            }
            catch { }
        }
        return "";
    }

    private static (string exe, int major, string home)? FindJava(int minMajor, bool wantJavac)
    {
        var roots = new List<string>
        {
            @"D:JAVA",
            @"C:Program FilesJava",
            @"C:Program FilesEclipse Adoptium",
            @"C:Program FilesZulu",
            @"D:Program FilesJava",
        };
        var hits = new List<(string exe, int major, string home)>();
        foreach (var root in roots)
        {
            if (!Directory.Exists(root)) continue;
            foreach (var home in Directory.GetDirectories(root))
            {
                var exeName = wantJavac ? "javac.exe" : "java.exe";
                var exe = Path.Combine(home, "bin", exeName);
                var javaExe = Path.Combine(home, "bin", "java.exe");
                if (!File.Exists(javaExe)) continue;
                if (wantJavac && !File.Exists(exe)) continue;
                var major = JavaMajorVersion(javaExe);
                if (major >= minMajor)
                {
                    hits.Add((wantJavac ? exe : javaExe, major, home));
                }
            }
        }
        var pathTool = wantJavac ? FindOnPath("javac.exe") : FindOnPath("java.exe");
        if (pathTool.Length > 0)
        {
            var home = Path.GetDirectoryName(Path.GetDirectoryName(pathTool)) ?? "";
            var major = JavaMajorVersion(Path.Combine(home, "bin", "java.exe"));
            if (major >= minMajor) hits.Add((pathTool, major, home));
        }
        return hits.OrderByDescending(h => h.major).FirstOrDefault();
    }

    private static string GetRepoParent() => Path.GetDirectoryName(RepoRoot) ?? RepoRoot;

    public static string? FindForgeJar()
    {
        var probes = new[]
        {
            Path.Combine(RepoRoot, "_tmp", "ysm", "port", ".gradle", "minecraft"),
            Path.Combine(GetRepoParent(), "_tmp", "ysm", "port", ".gradle", "minecraft"),
        };
        foreach (var probe in probes)
        {
            if (!Directory.Exists(probe)) continue;
            var hit = Directory.GetFiles(probe, "forgeBin-1.10.2-*.jar").FirstOrDefault();
            if (hit is not null) return hit;
        }
        return null;
    }

    public static string? FindSrg()
    {
        var probes = new[]
        {
            Path.Combine(GetRepoParent(), "_tmp", "ysm", "gradle-home", "caches", "minecraft", "de", "oceanlabs", "mcp", "mcp_stable", "29", "1.10.2", "srgs"),
            Path.Combine(RepoRoot, "_tmp", "ysm", "gradle-home", "caches", "minecraft", "de", "oceanlabs", "mcp", "mcp_stable", "29", "1.10.2", "srgs"),
        };
        foreach (var probe in probes)
        {
            var f = Path.Combine(probe, "mcp-srg.srg");
            if (File.Exists(f)) return f;
        }
        return null;
    }

    public static string? FindFart()
    {
        var inLibs = Directory.Exists(Path.Combine(RepoRoot, "libs"))
            ? Directory.GetFiles(Path.Combine(RepoRoot, "libs"), "ForgeAutoRenamingTool*.jar").FirstOrDefault()
            : null;
        if (inLibs is not null) return inLibs;
        var pcl = @"D:pcllibraries
etminecraftforgeForgeAutoRenamingTool";
        if (Directory.Exists(pcl))
        {
            return Directory.GetFiles(pcl, "*-all.jar", SearchOption.AllDirectories).FirstOrDefault();
        }
        return null;
    }

    private static IEnumerable<CheckItem> ScanBuild()
    {
        var javac = FindJava(17, wantJavac: true);
        yield return new CheckItem(
            "构建", "JDK 17 或更高（javac）",
            "build.ps1 用 javac --release 21 编译全部源码",
            javac is null ? CheckState.Missing : CheckState.Ok,
            javac is null ? "未在常见目录或 PATH 中找到" : $"JDK {javac.Value.major}：{javac.Value.home}",
            ManualHint: "安装任意 JDK 17+（推荐 21 或 25），或在路径设置里告诉我它的位置");

        var py = FindOnPath("python.exe");
        yield return new CheckItem(
            "构建", "Python 3",
            "构建里的十几个处理工具（AT 改写、fastutil 裁剪等）是 Python 脚本",
            py.Length > 0 ? CheckState.Ok : CheckState.Missing,
            py.Length > 0 ? py : "PATH 上没有 python.exe",
            ManualHint: "安装 Python 3 并加入 PATH，可用 winget install Python.Python.3.12");

        var forge = FindForgeJar();
        yield return new CheckItem(
            "构建", "反混淆 Forge jar（forgeBin）",
            "编译期对齐 1.10.2 的 MCP 名；由 ForgeGradle 2.3 的 setup 产出",
            forge is null ? CheckState.Missing : CheckState.Ok,
            forge ?? "未找到 forgeBin-1.10.2-*.jar",
            ManualHint: "把现成的 forgeBin jar 放到 _tmp 的 ysm/port/.gradle/minecraft 目录，或在路径设置里指定");

        var srg = FindSrg();
        yield return new CheckItem(
            "构建", "MCP stable_29 SRG 表",
            "把 MCP 名重映射到生产客户端的 SRG 名（reobf 必需）",
            srg is null ? CheckState.Missing : CheckState.Ok,
            srg ?? "未找到 mcp-srg.srg",
            ManualHint: "从 MCP 官方数据下载 stable_29，或复制现成文件到 _tmp 对应目录");

        var fart = FindFart();
        yield return new CheckItem(
            "构建", "ForgeAutoRenamingTool",
            "打包前把类名重映射回 SRG",
            fart is null ? CheckState.Missing : CheckState.Ok,
            fart ?? "未找到 ForgeAutoRenamingTool jar",
            AutoFixUrl: "https://github.com/MinecraftForge/ForgeAutoRenamingTool/releases",
            ManualHint: "从 GitHub 下载 0.1.22 放入 libs/ 目录即可被构建识别");

        var libsOk = Directory.Exists(Path.Combine(RepoRoot, "libs"))
                     && Directory.Exists(Path.Combine(RepoRoot, "src"));
        yield return new CheckItem(
            "构建", "源码仓库（src/ libs/ tools/）",
            "构建的输入本体",
            libsOk ? CheckState.Ok : CheckState.Missing,
            libsOk ? RepoRoot : $"在 {RepoRoot} 未找到 src/ 与 libs/",
            ManualHint: "把本工具放在源码仓库内运行，或在路径设置里指定仓库位置");
    }

    private static string? FindHarnessRoot()
    {
        var candidates = new List<string>
        {
            @"D:pcllibrariesiogithublwjgl3ify1102",
        };
        var userLibs = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
            "AppData", "Roaming", ".minecraft", "libraries", "io", "github", "lwjgl3ify1102");
        candidates.Add(userLibs);
        return candidates.FirstOrDefault(Directory.Exists);
    }

    private static bool CheckLwjgl3(string? harnessRoot, out string detail)
    {
        var groups = new[] { "lwjgl_3_3_3", "lwjgl_glfw_3_3_3", "lwjgl_openal_3_3_3", "lwjgl_stb_3_3_3", "lwjgl_tinyfd_3_3_3" };
        if (harnessRoot is null)
        {
            detail = "找不到 lwjgl3ify1102 库目录";
            return false;
        }
        int found = groups.Count(g => Directory.Exists(Path.Combine(harnessRoot, g)));
        detail = $"{harnessRoot}（{found}/{groups.Length} 组）";
        return found == groups.Length;
    }

    private static IEnumerable<CheckItem> ScanRuntime()
    {
        var java25 = FindJava(21, wantJavac: false);
        yield return new CheckItem(
            "运行", "Java 21+（游戏运行时）",
            "lwjgl3ify 核心模组按 class 61 编译，Java 8 装不上；RFB 需要 21+",
            java25 is null ? CheckState.Missing : CheckState.Ok,
            java25 is null ? "未找到 21+ 的 java.exe" : $"Java {java25.Value.major}：{java25.Value.home}",
            ManualHint: "主实例用 Java 25。装好后游戏运行时选它，不要用 PCL 默认的 Java 8");

        var harnessRoot = FindHarnessRoot();
        var needHarness = new[] { "harness-boot-1.0.0.jar", "harness-pre-1.0.0.jar", "harness-post-1.0.0.jar" };
        int harnessFound = 0;
        string harnessDetail;
        if (harnessRoot is null)
        {
            harnessDetail = "未找到 io/github/lwjgl3ify1102 库目录";
        }
        else
        {
            harnessFound = needHarness.Count(n => File.Exists(Path.Combine(harnessRoot, n, "1.0.0", n + ".jar"))
                                             || File.Exists(Path.Combine(harnessRoot, n)));
            harnessDetail = $"{harnessRoot}（{harnessFound}/3）";
        }
        yield return new CheckItem(
            "运行", "RetroFuturaBootstrap 启动链",
            "harness-boot/pre/post：系统类加载器与 notch 到 SRG 的桥接",
            harnessFound == 3 ? CheckState.Ok : CheckState.Missing,
            harnessDetail,
            ManualHint: "三个 jar 放入 libraries/io/github/lwjgl3ify1102 下对应目录，并加进启动 classpath 最前面");

        var lwjglOk = CheckLwjgl3(harnessRoot, out string lwjglDetail);
        yield return new CheckItem(
            "运行", "LWJGL 3.3.3 全家桶",
            "替代 1.10.2 原生 LWJGL 2；渲染全靠它",
            lwjglOk ? CheckState.Ok : CheckState.Missing,
            lwjglDetail,
            ManualHint: "lwjgl/glfw/openal/opengl/stb/tinyfd 六组（含 natives）放入 lwjgl3ify1102 库目录");

        var modsDir = Path.Combine(GameDir, "mods");
        var haveBooter = Directory.Exists(modsDir) && Directory.GetFiles(modsDir, "*mixinbooter*.jar").Length > 0;
        var haveCompat = Directory.Exists(modsDir) && Directory.GetFiles(modsDir, "lwjgl3ify-1.10.2-port*.jar").Length > 0;
        var haveMod = Directory.Exists(modsDir) && Directory.GetFiles(modsDir, "Protactinium*.jar").Length > 0;
        int modsFound = (haveBooter ? 1 : 0) + (haveCompat ? 1 : 0) + (haveMod ? 1 : 0);
        yield return new CheckItem(
            "运行", "游戏 mods（MixinBooter + lwjgl3ify-port + Protactinium）",
            "Mixin 框架、LWJGL2 兼容层与本模组本体",
            modsFound == 3 && GameDir.Length > 0 ? CheckState.Ok : CheckState.Missing,
            GameDir.Length == 0 ? "未找到游戏实例（mods 目录），请在路径设置里指定" : $"{modsDir}（{modsFound}/3）",
            ManualHint: "在路径设置里指定你的 1.10.2 Forge 实例目录");

        var nativesOk = false;
        string nativesDetail = "未检测";
        if (GameDir.Length > 0)
        {
            var nat = Directory.GetDirectories(GameDir, "*-natives").FirstOrDefault();
            if (nat is not null)
            {
                var dllCount = Directory.GetFiles(nat, "*.dll").Length;
                nativesOk = dllCount >= 10;
                nativesDetail = $"{nat}（{dllCount} 个 DLL）";
            }
            else
            {
                nativesDetail = "实例目录下没有 *-natives 文件夹";
            }
        }
        yield return new CheckItem(
            "运行", "LWJGL3 natives 目录",
            "启动参数 -Djava.library.path 指向的 DLL 集",
            nativesOk ? CheckState.Ok : CheckState.Missing,
            nativesDetail,
            ManualHint: "从可用的实例复制 natives 文件夹，或从 LWJGL3 官方 3.3.3 包提取");
    }
}
