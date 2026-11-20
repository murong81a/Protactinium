using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ProtactiniumSetup.Controls;

namespace ProtactiniumSetup.Pages;

public sealed partial class RuntimePage : Page
{
    public RuntimePage()
    {
        InitializeComponent();
        foreach (var item in EnvScanner.ScanAll().Where(i => i.Group == "运行"))
        {
            List.Children.Add(new CheckItemControl { Item = item });
        }
    }
}
