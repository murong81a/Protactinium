using System;
using System.IO;
using Microsoft.UI.Xaml;

namespace ProtactiniumSetup;

public partial class App : Application
{
    public static Window? MainWin { get; private set; }

    public App()
    {
        // WinAppSDK unpacks its runtime next to the exe; pin the base directory before any
        // WindowsAppSDK type is touched.
        Environment.SetEnvironmentVariable("MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY", AppContext.BaseDirectory);

        // Any failure from here on leaves a log next to the exe. A silent startup failure with
        // nothing on screen is indistinguishable from "the tool does not work", which is what
        // the log exists to prevent.
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log("UnhandledException", e.ExceptionObject);
        System.Threading.Tasks.TaskScheduler.UnobservedTaskException += (_, e) => Log("UnobservedTaskException", e.Exception);

        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            Log("InitializeComponent", ex);
            throw;
        }
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            MainWin = new MainWindow();
            MainWin.Activate();
        }
        catch (Exception ex)
        {
            Log("OnLaunched", ex);
            throw;
        }
    }

    private static void Log(string stage, object? error)
    {
        try
        {
            var path = Path.Combine(AppContext.BaseDirectory, "ProtactiniumSetup-crash.log");
            File.AppendAllText(path,
                DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " [" + stage + "] " + error + Environment.NewLine);
        }
        catch
        {
            // The log is best effort; the original failure is what matters and it is rethrown.
        }
    }
}
