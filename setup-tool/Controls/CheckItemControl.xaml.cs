using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace ProtactiniumSetup.Controls;

public sealed partial class CheckItemControl : UserControl
{
    public static readonly DependencyProperty ItemProperty =
        DependencyProperty.Register(nameof(Item), typeof(CheckItem), typeof(CheckItemControl), new PropertyMetadata(null, OnItemChanged));

    public CheckItem? Item
    {
        get => (CheckItem)GetValue(ItemProperty);
        set => SetValue(ItemProperty, value);
    }

    public CheckItemControl()
    {
        InitializeComponent();
    }

    private static void OnItemChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is CheckItemControl c && e.NewValue is CheckItem item)
        {
            c.Apply(item);
        }
    }

    private static SolidColorBrush FromHex(uint argb) => new(Microsoft.UI.ColorHelper.FromArgb(255, (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb));

    private void Apply(CheckItem item)
    {
        NameText.Text = item.Name;
        DetailText.Text = item.Detail;
        WhyText.Text = item.Why;

        var okBrush = FromHex(0x0F7B0F);
        var warnBrush = FromHex(0x9D5D00);
        var badBrush = FromHex(0xC42B1C);

        StatusDot.Fill = item.State switch
        {
            CheckState.Ok => okBrush,
            CheckState.Optional => warnBrush,
            _ => badBrush,
        };

        if (item.State != CheckState.Ok && item.ManualHint is { Length: > 0 } hint)
        {
            HintBox.Visibility = Visibility.Visible;
            HintText.Text = hint;
        }
        else
        {
            HintBox.Visibility = Visibility.Collapsed;
        }

        if (item.State != CheckState.Ok && item.AutoFixUrl is { Length: > 0 } url)
        {
            DownloadLink.Visibility = Visibility.Visible;
            DownloadLink.NavigateUri = new System.Uri(url);
        }
        else
        {
            DownloadLink.Visibility = Visibility.Collapsed;
        }
    }
}
