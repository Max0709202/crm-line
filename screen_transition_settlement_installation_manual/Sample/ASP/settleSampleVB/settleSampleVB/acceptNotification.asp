<%@ LANGUAGE="VBScript" %>
<%
    ' サンプルコード
    ' 完了通知受領
    '
    ' 画面遷移型BitCash決済サービスでは、
    ' BitCashのひらがなID入力画面等の決済画面はBitCash決済システムがエンドユーザに対して出力（表示）します。
    ' エンドユーザがひらがなIDを入力、またはマイビットキャッシュにログインする事で決済処理（BitCashの残高を減算）が行われ、
    ' 決済処理完了直後に加盟店様の指定のURLを呼び出し決済完了情報を通知します。
    ' BitCash決済システムから加盟店様指定のURLへの通知はHTTP(S) POSTメソッドを利用して行います。
    ' 加盟店様側指定のURLで通知を受領できた場合はHTTPレスポンスコードで200（OK）を返却します。
    ' HTTPレスポンスコードが200以外の場合は通知を受領できなかったものとして規定時間（約5分間）の間は通知のリトライを行います。
    ' 完了通知はBitCash決済システムから加盟店様システム(指定のURL)に対して行われるため、
    ' FireWall等で通信を制限している場合はBitCashシステムからの通信を許可するようにしてください。
    ' 加盟店様のセキュリティポリシー等により外部からのインバウンド通信を許可できないような場合は、
    ' 完了通知を利用せずに「決済確認処理」で代替する事も可能です。
    ' ただし、その場合はリアルタイム性が損なわれる事にご注意ください。
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

	'	ビットキャッシュから送信されるパラメータ（エンコードされているため、使用時はデコードが必要）
	'	tran_id			決済を一意に識別できるトランザクションID
	'	order_id		決済開始処理にて加盟店様から送信された発注コード
	'	settle_date		決済日時（yyyy/mm/dd h24:mi:ss形式）
	'	price			決済金額
	'	hash			パラメータ改ざん検知のためのハッシュ値
    tranId = Request.Form("tran_id")
    orderId = Request.Form("order_id")
    settleDate = Request.Form("settle_date")
    price = Request.Form("price")
    hash = Request.Form("hash")

    '受信パラメータ出力
    receiveOutSep = ","
    receiveOutHeaderString = "tran_id" & receiveOutSep
	receiveOutHeaderString = receiveOutHeaderString & "order_id" & receiveOutSep
	receiveOutHeaderString = receiveOutHeaderString & "settle_date" & receiveOutSep 	
	receiveOutHeaderString = receiveOutHeaderString & "price" & receiveOutSep 	 
	receiveOutHeaderString = receiveOutHeaderString & "hash"
    receiveOutDataString = tranId & receiveOutSep    
	receiveOutDataString = receiveOutDataString & orderId & receiveOutSep
	receiveOutDataString = receiveOutDataString & settleDate & receiveOutSep 	
	receiveOutDataString = receiveOutDataString & price & receiveOutSep 	
	receiveOutDataString = receiveOutDataString & hash
    Set objFso  = CreateObject("Scripting.FileSystemObject")
    receiveFilePath = "/logs/settle/accept_notification_receive_param.csv"
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

    '改ざんチェック
    '悪意のある第三者が不正なパラメータを設定しリクエストを送信するケースがあるため
	'ハッシュ値を用いてチェックを行い、不正な呼び出しや改ざんを防止します。

    '導入キットに含まれている通知キー
    notifyKey = "SAMPLE_notify_key"
    'ハッシュ値生成用文字列 tran_id=xxx&order_id&settle_date=xxx&price=xxx加盟店通知キー
    hashSource = "tran_id=" & UrlEncode(tranId)
    hashSource = hashSource & "&order_id=" & UrlEncode(orderId)
    hashSource = hashSource & "&settle_date=" & UrlEncode(settleDate)
    hashSource = hashSource & "&price=" & UrlEncode(price)
    hashSource = hashSource & notifyKey

    'ハッシュ値生成（サンプルでは.NETFrameworkを用いています
    hashSourceBytes = CreateObject("System.Text.UTF8Encoding").GetBytes_4(hashSource)

    Set SHA1 = CreateObject("System.Security.Cryptography.SHA1CryptoServiceProvider")
    SHA1.ComputeHash_2(hashSourceBytes)

    Set MSXML = CreateObject("MSXML2.DOMDocument")
    Set EL = MSXML.CreateElement("tmp")
    EL.DataType = "bin.hex"
    EL.NodeTypedValue = SHA1.Hash
    hashString = EL.Text

    IF StrComp(hashString, hash) <> 0 then
        '改ざんされていた場合
        'エラー処理

    Else
        '加盟店様側処理
	    'トランザクションID、オーダーIDを元に購入者および購入情報を特定し
	    '商品の受け渡しを行って、支払処理を完了させて下さい。

	 END IF
    
    'ビットキャッシュへの応答（HTTPレスポンスコード200）
    Response.Status = "200 OK"

    'URLエンコード
    Function UrlEncode(strSource)

        Dim lngSourceSize                                          'strSource の文字数
        Dim strArray()                                             '変換された文字列を格納する配列
        Dim strSingle                                              '抜き出された 1 文字を格納する変数
        Dim i                                                      'ループカウンタ
        Dim intAsc                                                 '文字コード
        Dim strHex                                                 '16 進数に変換した文字コード
        Dim lngHexLength                                           'strHex の文字数
        Dim lngCount                                               'strArray の格納位置

        lngSourceSize = Len(strSource)
        If lngSourceSize = 0 Then Exit Function
        ReDim strArray(lngSourceSize * 6 - 1)                   'strArray() のサイズを予約。文字数 * 6 にしておく。
    
        For i = 1 To lngSourceSize Step 1                       '引数 strSource の文字数分ループをまわす
            strSingle = Mid(strSource, i, 1)                    'i 文字目の 1 文字を strSource から抜き出す
            intAsc = Asc(strSingle)                             '文字コードを取得
            If intAsc = &H20 Then                               '文字が半角空白文字の場合
                strArray(lngCount) = "+"                        '"+" を代わりに入れる
            ElseIf (intAsc >= &H40 And intAsc <= &H5A) Or _
            (intAsc >= &H61 And intAsc <= &H7A) Or _
            (intAsc >= &H30 And intAsc <= &H39) Or _
                intAsc = &H2A Or intAsc = &H2D Or _
                intAsc = &H2E Or intAsc = &H5F Then                '無変換文字だった場合
                strArray(lngCount) = strSingle
            Else
                strArray(lngCount) = "%"
                strHex = Hex(intAsc)                            '16 進文字コードを取得する
                lngHexLength = Len(strHex)
                If lngHexLength = 4 Then                        '2 バイト文字の場合
                    strArray(lngCount + 1) = Left(strHex, 2)
                    strArray(lngCount + 2) = "%"
                    strArray(lngCount + 3) = Right(strHex, 2)
                    lngCount = lngCount + 3
                ElseIf lngHexLength = 2 Then                    '1 バイト文字の場合
                    strArray(lngCount + 1) = strHex
                    lngCount = lngCount + 1
                Else
                    strArray(lngCount + 1) = "0"
                    strArray(lngCount + 2) = strHex
                    lngCount = lngCount + 2
                End If
            End If
            lngCount = lngCount + 1
        Next
    
        ReDim Preserve strArray(lngCount - 1)
        UrlEncode = Join(strArray, "")                          'URL エンコードした文字列を返す

    End Function
%>

