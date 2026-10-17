<%@ Page Language="C#" AutoEventWireup="true" CodeBehind="SelectMethod.aspx.cs" Inherits="settleSampleC.SelectMethod" %>

<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml">
<head runat="server">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>決済API-javaサンプル | 2.支払い方法選択画面</title>
</head>
<body>
    <h3>決済API-javaサンプル | 2.支払い方法選択画面</h3>
    <form id="form2" runat="server" method="post" name="form2">
        <div>    
            <br />
            <h3>お支払い方法を選択してください</h3>
            <br />

            <asp:RadioButtonList ID="MethodKind" runat="server">
                <asp:ListItem Value="1">BitCash</asp:ListItem>
                <asp:ListItem Value="2">クレジットカード</asp:ListItem>
            </asp:RadioButtonList>


            <br />


        </div>
        <div>
            <asp:Button ID="ButtonSelectMethod" runat="server" Text="次へ" OnClick="ButtonSelectMethod_Click" />
            <asp:HiddenField ID="HiddenItemCode" runat="server" />
            <asp:HiddenField ID="HiddenItemName" runat="server" />
            <asp:HiddenField ID="HiddenItemRating" runat="server" />
            <asp:HiddenField ID="HiddenItemPrice" runat="server" />
        </div>
    </form>
</body>
</html>
