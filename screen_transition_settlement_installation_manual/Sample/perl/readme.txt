ファイル構成

settleSample/
	selectItem.pl・・・購入商品選択画面サンプル
	selectMethod.pl・・・購入方法選択画面サンプル
	confirm.pl・・・購入確認画面サンプル
	settle.pl・・・決済準備処理、弊社側決済画面へのリダイレクト処理サンプル
	acceptNotification.pl・・・決済完了通知受領サンプル
	complete.pl・・・決済完了画面サンプル
	confirmSettle.pl・・・決済確認処理サンプル
	refund.pl・・・決済取消対象指定画面サンプル
	refundSettle.pl・・・決済取消処理サンプル
	
必要モジュール

	Digest::SHA1
	# http://search.cpan.org

	※モジュールのインストール方法
	perl -MCPAN -e shell
	install Digest::SHA1
