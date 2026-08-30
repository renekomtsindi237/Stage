Map<String, dynamic> unwrapApiMap(dynamic data) {
  if (data is! Map) return <String, dynamic>{};
  final map = Map<String, dynamic>.from(data);
  final nested = map['data'];
  if (map.containsKey('success') && nested is Map) {
    return Map<String, dynamic>.from(nested);
  }
  return map;
}

List<dynamic> unwrapApiList(dynamic data) {
  if (data is List) return data;
  if (data is Map && data['data'] is List) {
    return data['data'] as List<dynamic>;
  }
  return const [];
}
