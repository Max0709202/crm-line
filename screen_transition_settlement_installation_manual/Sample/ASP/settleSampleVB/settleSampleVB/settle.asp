<%@ LANGUAGE="VBScript" %>
<%
    On Error Resume Next

    ' サンプルコード
    ' 決済開始処理
    '
    ' 決済に必要な情報をBitCash決済システムに対してHTTP(S)プロトコルのGETまたはPOSTメソッドを利用して送信します。
    ' BitCash決済システムは加盟店様から送信された決済情報を元に決済の準備を行い、
    ' 処理結果としてトランザクションIDや決済画面のURLを返却します。
    ' 決済システムからの応答はHTTP(S)のレスポンスボディに
    ' 「パラメータ名=パラメータ値&パラメータ名=パラメータ値&…」という形式で出力されますので、
    ' 応答を受信した加盟店様側ではレスポンスボディの文字列を「&」で分割し、
    ' 更に分割したそれぞれの文字列を「=」で分割する事によりトランザクションIDや決済画面のURLを取得する事ができます。
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

	'決済種別を元に決済情報を取得（DBから取得など）
	'※サンプルではハードコーディングをしております。

    methodKind = Request.Form("methodKind")
    itemName = Request.Form("itemName")
    itemRating = Request.Form("itemRating")
    itemPrice = Request.Form("itemPrice")

    select case methodKind
        case 1
			'決済種別：BitCashの場合
			action = "Settle"
			methodName = "BitCash"                
    end select


    '決済準備処理で接続するURL生成
    Dim myPostData

    prepareSettleURL = "https://settle.bitcash.co.jp/settle/tri?act=prepareSettle"

    '加盟店様の認証に使用する情報
    shopId = "SAMPLE9999" 			'契約時にお渡しした加盟店ID
    shopKey = "SAMPLE_key"      '契約時にお渡しした加盟店キー

    'ビットキャッシュ決済画面の表示有効期限の設定(秒単位で設定。サンプルは10分)。任意の期限を設定してください
    timeOut = 600

    '決済完了通知URL ユーザ様がビットキャッシュ決済画面にてお支払が完了した際、ビットキャッシュより通知を行うURLです
    '定期実行のみでビットキャッシュからのお支払完了通知が不要の場合は、「none」を指定してください
    notifyUrl = "http://example.jp/acceptNotification.asp"

    'お支払完了後、加盟店様サイトへ戻る際のURLです
    returnUrl = "http://example.jp/complete.asp"

    'ユーザ様がビットキャッシュでのお支払の前に加盟店様サイトへ戻る際のURLです
    'サンプルでは、商品選択画面を設定しています
    cancelUrl = "http://example.jp/selectItem.asp"

    'オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
    orderId = "ORDER_" & Replace(Replace(Replace(Now(), "/", ""), ":", ""), " ", "")

    'POSTするパラメータの文字列作成
    myPostData = "shop_id=" & shopId 		                ' 契約時にお渡しした加盟店ID
	myPostData = myPostData & "&shop_key=" & shopKey 		' 契約時にお渡しした加盟店キー
	myPostData = myPostData & "&price=" & itemPrice 		' お支払金額
	myPostData = myPostData & "&rating=" & itemRating 		' 商品のレイティング
	myPostData = myPostData & "&order_id=" & orderId 		' オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
	myPostData = myPostData & "&timeout=" & timeOut 
	myPostData = myPostData & "&notify_url=" & notifyUrl 
	myPostData = myPostData & "&return_url=" & returnUrl 
	myPostData = myPostData & "&cancel_url=" & cancelUrl 


    '送信パラメータ出力
    sendOutSep = ","
    sendOutHeaderString = "shop_id" & sendOutSep
	sendOutHeaderString = sendOutHeaderString & "shop_key" & sendOutSep
	sendOutHeaderString = sendOutHeaderString & "price" & sendOutSep 	
	sendOutHeaderString = sendOutHeaderString & "rating" & sendOutSep 	
	sendOutHeaderString = sendOutHeaderString & "order_id" & sendOutSep
	sendOutHeaderString = sendOutHeaderString & "timeout" & sendOutSep 
	sendOutHeaderString = sendOutHeaderString & "notify_url" & sendOutSep 
	sendOutHeaderString = sendOutHeaderString & "return_url" & sendOutSep 
	sendOutHeaderString = sendOutHeaderString & "cancel_url"
    sendOutDataString = shopId & sendOutSep    
	sendOutDataString = sendOutDataString & shopKey & sendOutSep
	sendOutDataString = sendOutDataString & itemPrice & sendOutSep 	
	sendOutDataString = sendOutDataString & itemRating & sendOutSep 	
	sendOutDataString = sendOutDataString & orderId & sendOutSep
	sendOutDataString = sendOutDataString & timeOut & sendOutSep 
	sendOutDataString = sendOutDataString & notifyUrl & sendOutSep 
	sendOutDataString = sendOutDataString & returnUrl & sendOutSep 
	sendOutDataString = sendOutDataString & cancelUrl

    Dim objFso 
    Dim objFile 

    '送信パラメータ出力（証跡として出力しておきます）
    Set objFso  = CreateObject("Scripting.FileSystemObject")
    sendFilePath = "/logs/settle/start_settle_send_param.csv"

    If not(objFso.FileExists(sendFilePath)) Then
        'ファイルが存在しない場合ヘッダ出力
        Set objFile  = objFso.OpenTextFile(sendFilePath, 2, true)
        objFile.WriteLine sendOutHeaderString
    Else
        Set objFile  = objFso.OpenTextFile(sendFilePath, 8, true)
    End If
    objFile.WriteLine sendOutDataString
    objFile.Close

    Set objFile = Nothing
    Set objFso  = Nothing


    'リクエスト送信
    Set objXML = Server.CreateObject("MSXML2.ServerXMLHTTP")
    objXML.open "POST", prepareSettleURL, False
    objXML.setRequestHeader "Content-Type", " application/x-www-form-urlencoded"
    objXML.setRequestHeader "Content-Length", "length"
    objXML.send myPostData
    strXMLDoc = objXML.responseText
    intRet = objXML.status
    Set objXML = Nothing
    

    '受信したレスポンスを分解
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

    '受信パラメータ出力
    Set objFso  = CreateObject("Scripting.FileSystemObject")
    receiveFilePath = "/logs/settle/start_settle_receive_param.csv"
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

    statusString = hash("status_string")
    settleUrl = hash("settle_url")
    IF StrComp(statusString, "SUCCESS") = 0 then
        '結果が正常な場合リダイレクト

        ' 	決済準備処理が成功した場合
		' 	status_code:"0"				ステータスコード：処理成功
		'	status_string:"SUCCEESS"	ステータス文字列：処理成功
		'	tran_id						決済を一意に識別できるトランザクションID
		'	settle_url					決済画面URL(暗号化トランザクションID付加)

		' ユーザ様のお支払情報を一意に特定するトランザクションIDを返却します。
		' 問い合わせ、お支払の取消、お支払の確認にも使用いただけますので、加盟店様システム側で保管してください。

		' hash("tran_id")

		' 決済画面URL(settleUrl=)後のパラメータ(決済画面URL)へリクエストをリダイレクトします
		' ※パラメータはエンコードされているためデコードが必要です

        Response.Redirect UrlDecode(settleUrl)
    ELSE 
        'エラーの場合
%>
<html>
<head>
<title>決済API-javaサンプル | 6.決済エラー画面</title>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<body>
	<div id="content">
		<h3>決済API-javaサンプル | 6.決済エラー画面</h3>

        <h3>お手続きに失敗しました</h3>

	</div>
</body>
</html>
<%        
    END IF


    'URLデコード関数
    Function UrlDecode(Source)
     On Error Resume Next
     sTmp=""
     iCount = 1
     lSrcLen=Len(Source)
     Do Until iCount > lSrcLen
         sChr = Mid(Source,iCount,1)
         iCount = iCount+1
         If sChr="+" Then
             sChr = " "
         ElseIf sChr="%" Then
             sHex = Mid(Source,iCount,2)
             iCount = iCount + 2
             iAsc = CByte("&H" & sHex)
             If (&H00 <= iAsc And iAsc <= &H80) Or _
                (&HA0 <= iAsc And iAsc <= &HDF) Then
                 '1バイト文字
                 sChr=Chr(iAsc)
             ElseIf (&H81 <= iAsc And iAsc <= &H9F) Or _
                (&HE0 <= iAsc And iAsc <= &HFF) Then
                 '2バイト文字
                 sChr = Mid(Source,iCount,1)
                 iCount = iCount + 1
                 If sChr="%" Then
                     sHex2 = Mid(Source,iCount,2)
                     iCount = iCount + 2
                 Else
                     sHex2 = Hex(Asc(sChr))
                     If Len(sHex2) = 1 Then
                         sHex2 = "0" & sHex2
                     End If
                 End If
                 sChr=Chr(CInt("&H" & sHex & sHex2))
             End If
         End If
         sTmp=sTmp & sChr
     Loop
     UrlDecode = sTmp
 End Function
%>

