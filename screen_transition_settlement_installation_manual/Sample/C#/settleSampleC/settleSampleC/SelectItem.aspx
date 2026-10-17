<%@ Page Language="C#" AutoEventWireup="true" CodeBehind="SelectItem.aspx.cs" Inherits="settleSampleC.SelectItem" %>

<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml">
<head runat="server">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>決済API-javaサンプル | 1.購入商品選択画面</title>
</head>
<body>
    <h3>決済API-javaサンプル | 1.購入商品選択画面</h3>
    <form id="form1" runat="server" method="post" name="form1">
        <div>    
            <br />
            <h3>購入する商品を選択してください</h3>
            <br />

            <asp:RadioButtonList ID="ItemCode" runat="server">
                <asp:ListItem Value="1">商品A:1,000円（全年齢）</asp:ListItem>
                <asp:ListItem Value="2">商品B:3,000円（全年齢）</asp:ListItem>
                <asp:ListItem Value="3">商品C:5,000円（全年齢）</asp:ListItem>
                <asp:ListItem Value="4">商品D:1,000円（20歳以上）</asp:ListItem>
                <asp:ListItem Value="5">商品E:3,000円（20歳以上）</asp:ListItem>
                <asp:ListItem Value="6">商品F:5,000円（20歳以上）</asp:ListItem>
            </asp:RadioButtonList>
        </div>
        <div>
            <asp:Button ID="ButtonSelectItem" runat="server" Text="次へ" OnClick="ButtonSelectItem_Click" />
        </div>
    </form>
</body>
</html>
