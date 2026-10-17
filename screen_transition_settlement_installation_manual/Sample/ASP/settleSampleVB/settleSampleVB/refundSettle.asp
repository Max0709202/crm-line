<%@ LANGUAGE="VBScript" %>
<%
    'サンプルコード
    '決済取消

    ' 取消対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
    ' 指定することで、エンドユーザが決済に利用したビットキャッシュに支払金額分の残高を戻します。
    ' 当サンプルでは、前画面より入力されたトランザクションIDの決済取消処理を行います。
    ' @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
    ' @author		BitCash
    ' @since		2014/1/10
    '
    ' < 免責事項 >
    ' ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
    ' 加盟店様のご利用環境に よっては利用できない場合があります。
    ' ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
    '
    ' ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては
    ' 一切責任を負わないものとします。
    '
    ' ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
    '
    ' ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。

    '確認対象トランザクションID（決済処理時に払い出される、ユーザ様のお支払情報を一意に特定するIDです。）
    '※サンプルのためハードコーディングしておりますが、適宜加盟店様システムDBからの取得等へご変更ください。   
    tranId = Request.Form("tranId")
    '決済準備処理で接続するURL生成
    Dim myPostData

    refundSettleURL = "https://settle.bitcash.co.jp/settle/tri?act=refundSettle"

    '加盟店様の認証に使用する情報
    shopId = "SAMPLE9999" 			'契約時にお渡しした加盟店ID
    shopKey = "SAMPLE_key"      '契約時にお渡しした加盟店キー

    'POSTパラメータ生成
    myPostData = "shop_id=" & shopId 		                ' 契約時にお渡しした加盟店ID
	myPostData = myPostData & "&shop_key=" & shopKey 		' 契約時にお渡しした加盟店キー
	myPostData = myPostData & "&tran_id=" & tranId 		

    'リクエスト送信
    Set objXML = Server.CreateObject("MSXML2.ServerXMLHTTP")
    objXML.open "POST", refundSettleURL, False
    objXML.setRequestHeader "Content-Type", " application/x-www-form-urlencoded"
    objXML.setRequestHeader "Content-Length", "length"
    objXML.send myPostData
    strXMLDoc = objXML.responseText
    intRet = objXML.status
    Set objXML = Nothing
    

    '受信したレスポンスを分解
    '決済取消処理結果（ステータスコード、ステータス文字列の詳細については導入マニュアルをご参照ください。）
	'   status_code				処理結果を示すステータスコード
	'	status_string			処理結果を示すステータス文字列
	'	settle_id				決済コード
	'	settle_date				決済日時（yyyy/mm/dd h24:mi:ss 形式）
	'	refund_id				リファンドコード
	'	refund_date				リファンド日時（yyyy/mm/dd h24:mi:ss 形式）
	'	cno						管理番号
    Dim aryStrings
    aryStrings = Split(strXMLDoc, "&")
    Dim hash
    receiveOutSep = ","

    Set hash = CreateObject("Scripting.Dictionary")
    For i = 0 To Ubound(aryStrings)
        Dim aryDatas
        aryDatas = Split(aryStrings(i), "=")
        hash.Add aryDatas(0), aryDatas(1)
        receiveOutHeaderString = receiveOutHeaderString & aryDatas(0) & receiveOutSep
        receiveOutDataString = receiveOutDataString & aryDatas(1) & receiveOutSep
    Next

    receiveOutHeaderString =  Left(receiveOutHeaderString, Len(receiveOutHeaderString) - 1) 
    receiveOutDataString =  Left(receiveOutDataString, Len(receiveOutDataString) - 1) 

    Dim objFso 
    Dim objFile 


    '受信パラメータ出力
    Set objFso  = CreateObject("Scripting.FileSystemObject")
    receiveFilePath = "/logs/settle/refund_param.csv"
    If not(objFso.FileExists(receiveFilePath)) Then
        'ファイルが存在しない場合ヘッダ出力
        Set objFile  = objFso.OpenTextFile(receiveFilePath, 2, true)
        objFile.WriteLine receiveOutHeaderString
    Else
        Set objFile  = objFso.OpenTextFile(receiveFilePath, 8, true)
    End If
    objFile.WriteLine receiveOutDataString
    objFile.Close

    Set objFile = Nothing
    Set objFso  = Nothing

    
    
    'ビットキャッシュへの応答（HTTPレスポンスコード200）
    Response.Status = "200 OK"


    
%>

<html lang="ja" >
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<head>
	<title>決済API-javaサンプル | 決済取消結果表示画面</title>
</head>
<body>
	<div id="content">
		<h3>決済API-javaサンプル | 決済取消結果表示画面</h3>
			<p>ステータス：<% = hash("status_string") %></p>
			<p>決済コード：<% = hash("settle_id") %></p>
			<p>決済日時：<% = hash("settle_date") %></p>
			<p>リファンドコード：<% = hash("refund_id") %></p>
			<p>リファンド日時：<% = hash("refund_date") %></p>
			<p>管理番号：<% = hash("cno") %></p>
	</div>
</body>
</html>