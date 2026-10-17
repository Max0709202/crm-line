<%@ Page Language="C#" AutoEventWireup="true" CodeBehind="Refund.aspx.cs" Inherits="settleSampleC.Refund" %>

<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml">
<head runat="server">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>決済API-javaサンプル | 決済取消対象入力画面</title>
</head>
<body>
    <h3>決済API-javaサンプル | 決済取消対象入力画面</h3>
    <form id="form1" runat="server" method="post" name="form1">
        <div>    
            <br />
            <h3>決済取消対象のトランザクションIDを入力してください</h3>
            <br />
            <asp:TextBox ID="TextBoxTranId" runat="server"></asp:TextBox>

        </div>
        <div>
            <asp:Button runat="server" Text="決済取消を行う" OnClick="ButtonRefund_Click" />
        </div>
    </form>
</body>
</html>
