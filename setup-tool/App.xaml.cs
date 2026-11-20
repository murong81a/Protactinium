using Microsoft.UI.Xaml;

namespace ProtactiniumSetup;

public partial class App : Application
{
    public static Window? MainWin { get; private set; }

    public App()
    {
        // WinAppSDK single-file publish requirement (see build warning): the runtime lookup
        // directory must be pinned before any WindowsAppSDK type is touched.
        System.Environment.SetEnvironmentVariable("MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY", AppContext.BaseDirectory);
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        MainWin = new MainWindow();
        MainWin.Activate();
    }
}
