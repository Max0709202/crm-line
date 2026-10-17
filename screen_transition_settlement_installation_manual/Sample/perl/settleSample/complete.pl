#!/usr/bin/perl
use strict;
use warnings;

###
# 決済-perlサンプル
#
# 決済完了画面のサンプルです。
# @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
# @author		BitCash
# @since		2014/1/10
#
# < 免責事項 >
# ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
# 加盟店様のご利用環境によっては利用できない場合があります。
# ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
# 
# ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては一切責任を負わないものとします。
# 
# ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
# 
# ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。

print <<"EOM";
Content-Type: text/html; charset=UTF-8\n\n
<html lang="ja" >
<head>
<title>決済-perlサンプル | 4.支払完了画面</title>
</head>
<body>
<div id="content">
<h3>決済-perlサンプル | 4.支払完了画面</h3>
<br />
<h3>決済が完了しました</h3>
<h3>お買い上げありがとうございました。</h3>
</div>
</body>
</html>
EOM
exit;