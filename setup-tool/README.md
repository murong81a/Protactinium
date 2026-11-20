# ProtactiniumSetup 环境检测工具

以 363FastTool 的 WinUI 3 风格编写的环境自检/补全工具。检测构建 Protactinium 与运行它
所需的全部依赖，缺什么一目了然，可选择自动补全或按提示手动处理。

## 构建本工具

需要 .NET 10 SDK（net10.0-windows10.0.26100.0，与参考项目相同）。

    dotnet build -c Release

产物在 bin/Release/net10.0-windows10.0.26100.0/win-x64/。

## 检测项

构建侧：JDK 17+（javac）、JDK 25（运行）、Python 3、FART、反混淆 Forge jar、MCP SRG 表。
运行侧：RetroFuturaBootstrap harness 三件、LWJGL 3.3.3 全家桶、MixinBooter、
lwjgl3ify-port、游戏实例位置。
