import 'package:flutter_test/flutter_test.dart';
import 'package:microrecouv/core/utils/uuid_v4.dart';

void main() {
  test('générateur UUID v4 disponible (smoke)', () {
    expect(uuidV4Pattern.hasMatch(generateUuidV4()), isTrue);
  });
}
