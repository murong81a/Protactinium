using System.IO;
using System.Text.Json;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;

namespace ProtactiniumSetup.Pages;

public sealed partial class PathsPage : Page
{
    private sealed record PathConfig(string? Repo, string? Game);

    public PathsPage()
    {
        InitializeComponent();
        LoadConfig();
    }

    private static string ConfigPath => Path.Combine(AppContext.BaseDirectory, "setup-paths.json");

    private void LoadConfig()
    {
        if (File.Exists(ConfigPath))
        {
            try
            {
                var cfg = JsonSerializer.Deserialize<PathConfig>(File.ReadAllText(ConfigPath));
                RepoBox.Text = cfg?.Repo ?? "";
                GameBox.Text = cfg?.Game ?? "";
            }
            catch { }
        }
    }

    private void SaveConfig()
    {
        var cfg = new PathConfig(
            string.IsNullOrWhiteSpace(RepoBox.Text) ? null : RepoBox.Text.Trim(),
            string.IsNullOrWhiteSpace(GameBox.Text) ? null : GameBox.Text.Trim());
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(cfg, new JsonSerializerOptions { WriteIndented = true }));
    }

    private async void OnBrowseRepo(object sender, RoutedEventArgs e) => await BrowseInto(RepoBox);

    private async void OnBrowseGame(object sender, RoutedEventArgs e) => await BrowseInto(GameBox);

    private async System.Threading.Tasks.Task BrowseInto(TextBox box)
    {
        var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.ComputerFolder };
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWin);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        var folder = await picker.PickSingleFolderAsync();
        if (folder is not null) box.Text = folder.Path;
    }

    private void OnSave(object sender, RoutedEventArgs e)
    {
        SaveConfig();
        SavedBar.IsOpen = true;
    }
}
