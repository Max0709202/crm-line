<%@ Page Language="C#" AutoEventWireup="true" CodeBehind="RefundComplete.aspx.cs" Inherits="settleSampleC.RefundComplete" %>

<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml">
<head runat="server">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>決済API-javaサンプル | 決済取消結果表示画面</title>
</head>
<body>
    <h3>決済API-javaサンプル | 決済取消結果表示画面</h3>
    <form id="form1" runat="server" method="post" name="form1">
        <div>    
            <br />
            <h3>決済取消が完了しました</h3>
            <br />
		    <p>ステータス：<asp:Label ID="LabelStatus" runat="server" Text=""></asp:Label></p>
		    <p>決済コード：<asp:Label ID="LabelSettleId" runat="server" Text=""></asp:Label></p>
		    <p>決済日時：<asp:Label ID="LabelSettleDate" runat="server" Text=""></asp:Label></p>
		    <p>リファンドコード：<asp:Label ID="LabelRefundId" runat="server" Text=""></asp:Label></p>
		    <p>リファンド日時：<asp:Label ID="LabelRefundDate" runat="server" Text=""></asp:Label></p>
		    <p>管理番号：<asp:Label ID="LabelCno" runat="server" Text=""></asp:Label></p>
        </div>
    </form>
</body>
</html>
