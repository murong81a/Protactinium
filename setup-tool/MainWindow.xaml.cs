using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;

namespace ProtactiniumSetup;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        ContentFrame.Navigate(typeof(Pages.HomePage));
    }

    private void NavView_ItemInvoked(NavigationView sender, NavigationViewItemInvokedEventArgs args)
    {
        var tag = (args.InvokedItemContainer.Tag as string) ?? "home";
        Type page = tag switch
        {
            "build" => typeof(Pages.BuildPage),
            "runtime" => typeof(Pages.RuntimePage),
            "paths" => typeof(Pages.PathsPage),
            _ => typeof(Pages.HomePage),
        };
        if (ContentFrame.CurrentSourcePageType != page)
        {
            ContentFrame.Navigate(page);
        }
    }
}
