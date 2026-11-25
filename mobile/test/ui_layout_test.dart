import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kongklang_mobile/ui/kk_ui.dart';
import 'package:kongklang_mobile/ui/onboarding.dart';

void main() {
  setUpAll(() async {
    await (FontLoader('NotoSansThai')
          ..addFont(rootBundle.load('assets/fonts/noto-sans-thai-0.ttf'))
          ..addFont(rootBundle.load('assets/fonts/noto-sans-thai-3.ttf')))
        .load();
    await (FontLoader(
      'MaterialIcons',
    )..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  const sizes = [
    Size(320, 640),
    Size(360, 640),
    Size(360, 800),
    Size(393, 873),
    Size(412, 915),
    Size(480, 1040),
    Size(600, 960),
    Size(800, 1280),
    Size(1024, 1366),
  ];

  for (final size in sizes) {
    testWidgets('core surfaces fit ${size.width}x${size.height}', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData(fontFamily: 'NotoSansThai'),
          home: Scaffold(
            body: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const KkPageHero(
                      title: 'DASHBOARD',
                      subtitle: 'ร้านคงคลัง · ตรวจสอบข้อมูลร้าน',
                      icon: Icons.space_dashboard_rounded,
                    ),
                    const SizedBox(height: 16),
                    const KkFeaturedCarousel(
                      children: [
                        GlassCard(child: Text('สินค้าใกล้หมด 4 รายการ')),
                        GlassCard(child: Text('สินค้าในร้าน 120 รายการ')),
                      ],
                    ),
                    const SizedBox(height: 16),
                    KkProductCard(
                      product: const {
                        'code': 'STORE-001',
                        'name': 'น้ำสมุนไพรสูตรดั้งเดิม',
                        'category': 'เครื่องดื่ม',
                        'quantity': 3,
                        'price': 15,
                      },
                      onTap: () {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.text('DASHBOARD'), findsOneWidget);
      if (size.width == 393) {
        await expectLater(
          find.byType(Scaffold),
          matchesGoldenFile('goldens/overview_393.png'),
        );
      }
    });
  }

  testWidgets('first-run guide completes on a compact phone', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var completed = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(fontFamily: 'NotoSansThai'),
        home: KkOnboarding(onDone: () => completed = true),
      ),
    );
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('ถัดไป'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('ถัดไป'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('เริ่มใช้งาน'));
    expect(completed, isTrue);
  });

  testWidgets('guide remains usable with larger text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(fontFamily: 'NotoSansThai'),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: const TextScaler.linear(1.3)),
          child: child!,
        ),
        home: KkOnboarding(onDone: () {}),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('ถัดไป'), findsOneWidget);
  });
}
