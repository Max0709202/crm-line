<%@ Page Language="C#" AutoEventWireup="true" CodeBehind="Confirm.aspx.cs" Inherits="settleSampleC.Confirm" %>

<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml">
<head runat="server">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>決済API-javaサンプル | 3.支払い確認画面</title>
</head>
<body>
    <h3>決済API-javaサンプル | 3.支払い確認画面</h3>
    <form id="form1" runat="server" method="post">
        <div>    
            <br />
            <h3>支払い内容を確認してください</h3>
            <br />

            
            <p>商品名：<asp:Label ID="LabelItemName" runat="server" Text=""></asp:Label></p>
		    <p>お支払い金額：<asp:Label ID="LabelItemPrice" runat="server" Text=""></asp:Label></p>
		    <p>お支払い方法：<asp:Label ID="LabelMethodName" runat="server" Text=""></asp:Label></p>

        </div>
        <div>
            <asp:Button ID="ButtonConfirm" runat="server" Text="決済" OnClick="ButtonConfirm_Click" />

            <asp:HiddenField ID="HiddenItemPrice" runat="server" />
            <asp:HiddenField ID="HiddenItemRating" runat="server" />
        </div>
    </form>
</body>
</html>
