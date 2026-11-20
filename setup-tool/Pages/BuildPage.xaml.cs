using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ProtactiniumSetup.Controls;

namespace ProtactiniumSetup.Pages;

public sealed partial class BuildPage : Page
{
    public BuildPage()
    {
        InitializeComponent();
        foreach (var item in EnvScanner.ScanAll().Where(i => i.Group == "构建"))
        {
            List.Children.Add(new CheckItemControl { Item = item });
        }
    }
}
