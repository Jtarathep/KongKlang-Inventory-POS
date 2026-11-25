import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiFailure implements Exception {
  const ApiFailure(this.message, this.status);
  final String message;
  final int status;
  @override
  String toString() => message;
}

class ApiService {
  ApiService([http.Client? client]) : _client = client ?? http.Client();
  final http.Client _client;
  Uri _uri(String route, [Map<String, String>? query]) =>
      Uri.base.resolve('/api/mobile/$route').replace(queryParameters: query);

  Future<dynamic> get(String route, [Map<String, String>? query]) async =>
      _decode(
        await _client
            .get(_uri(route, query))
            .timeout(const Duration(seconds: 15)),
      );

  Future<dynamic> receive(Map<String, dynamic> body, String csrf) async =>
      _decode(
        await _client
            .post(
              _uri('receive'),
              headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrf,
              },
              body: jsonEncode(body),
            )
            .timeout(const Duration(seconds: 20)),
      );

  Future<Map<String, dynamic>> ask(
    String question,
    String csrf, {
    String? previousQuestion,
  }) async => Map<String, dynamic>.from(
    _decode(
      await _client
          .post(
            Uri.base.resolve('/api/kk-ai/ask'),
            headers: {'Content-Type': 'application/json', 'X-CSRF-Token': csrf},
            body: jsonEncode({
              'question': question,
              'previousQuestion': previousQuestion,
            }),
          )
          .timeout(const Duration(seconds: 80)),
    ) as Map,
  );

  Future<void> logout(String csrf) async {
    final response = await _client
        .post(Uri.base.resolve('/logout'), body: {'_csrf': csrf})
        .timeout(const Duration(seconds: 15));
    if (response.statusCode != 200 && response.statusCode != 302) {
      throw ApiFailure('ออกจากระบบไม่สำเร็จ กรุณาลองใหม่', response.statusCode);
    }
  }

  dynamic _decode(http.Response response) {
    dynamic data;
    try {
      data = jsonDecode(utf8.decode(response.bodyBytes));
    } catch (_) {
      data = null;
    }
    if (response.statusCode == 401) {
      throw const ApiFailure('กรุณาเข้าสู่ระบบ KONGKLANG อีกครั้ง', 401);
    }
    if (response.statusCode < 200 ||
        response.statusCode >= 300 ||
        data == null) {
      throw ApiFailure(
        data is Map
            ? (data['message'] ?? data['detail'] ?? 'ไม่สามารถทำรายการได้')
                  .toString()
            : 'เชื่อมต่อ KONGKLANG ไม่สำเร็จ',
        response.statusCode,
      );
    }
    return data;
  }

  void close() => _client.close();
}
