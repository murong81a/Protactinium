using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ProtactiniumSetup.Controls;

namespace ProtactiniumSetup.Pages;

public sealed partial class HomePage : Page
{
    public HomePage()
    {
        InitializeComponent();
        Rescan();
    }

    private void Rescan()
    {
        BusyRing.Visibility = Visibility.Visible;
        var items = EnvScanner.ScanAll();
        BusyRing.Visibility = Visibility.Collapsed;

        BuildList.Children.Clear();
        RuntimeList.Children.Clear();

        foreach (var item in items.Where(i => i.Group == "构建"))
        {
            BuildList.Children.Add(new CheckItemControl { Item = item, Margin = new Thickness(0) });
        }
        foreach (var item in items.Where(i => i.Group == "运行"))
        {
            RuntimeList.Children.Add(new CheckItemControl { Item = item, Margin = new Thickness(0) });
        }

        int ok = items.Count(i => i.State == CheckState.Ok);
        int missing = items.Count(i => i.State == CheckState.Missing);
        SummaryText.Text = missing == 0
            ? $"全部 {items.Count} 项就绪。仓库：{EnvScanner.RepoRoot}"
            : $"共 {items.Count} 项：{ok} 项就绪，{missing} 项缺失。缺失项不补全将无法构建或运行。";
    }

    private void OnRescan(object sender, RoutedEventArgs e) => Rescan();

    private void OnAutoFix(object sender, RoutedEventArgs e)
    {
        // 一键补全：当前版本跳转路径页先配好位置；自动下载在后续版本加。
        Frame.Navigate(typeof(PathsPage));
    }
}
