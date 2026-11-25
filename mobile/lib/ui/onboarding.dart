import 'package:flutter/material.dart';

import 'kk_ui.dart';

class KkSplash extends StatefulWidget {
  const KkSplash({super.key});

  @override
  State<KkSplash> createState() => _KkSplashState();
}

class _KkSplashState extends State<KkSplash>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.stop();
      _controller.value = 1;
    } else {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xfff8fbff), Color(0xffe3edff), Color(0xffcbdfff)],
        ),
      ),
      child: SafeArea(
        child: Center(
          child: Semantics(
            label: 'กำลังตรวจสอบบัญชีและโหลดร้านค้า',
            liveRegion: true,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ScaleTransition(
                  scale: Tween<double>(begin: .94, end: 1.06).animate(
                    CurvedAnimation(
                      parent: _controller,
                      curve: Curves.easeInOut,
                    ),
                  ),
                  child: GlassContainer(
                    radius: 28,
                    padding: const EdgeInsets.all(20),
                    child: Image.asset(
                      'assets/kongklang-logo.png',
                      width: 72,
                      height: 72,
                    ),
                  ),
                ),
                const SizedBox(height: 25),
                const Text(
                  'KONGKLANG',
                  style: TextStyle(
                    color: KkColors.navy,
                    fontSize: 27,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 7),
                const Text(
                  'กำลังเตรียมพื้นที่ทำงานของคุณ',
                  style: TextStyle(color: KkColors.muted),
                ),
                const SizedBox(height: 24),
                const SizedBox(
                  width: 28,
                  height: 28,
                  child: CircularProgressIndicator(
                    color: KkColors.blue,
                    strokeWidth: 2.5,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class KkOnboarding extends StatefulWidget {
  const KkOnboarding({super.key, required this.onDone});
  final VoidCallback onDone;

  @override
  State<KkOnboarding> createState() => _KkOnboardingState();
}

class _KkOnboardingState extends State<KkOnboarding> {
  final _controller = PageController();
  int _page = 0;

  static const _pages = [
    (
      Icons.storefront_rounded,
      'จัดการร้านในที่เดียว',
      'สินค้าและข้อมูลร้านของคุณอยู่ในพื้นที่ทำงานเดียวกัน',
    ),
    (
      Icons.inventory_2_rounded,
      'เห็นสต็อกทุกล็อต',
      'รับสินค้า ตรวจยอดคงเหลือ และติดตามล็อตได้สะดวก',
    ),
    (
      Icons.auto_awesome_rounded,
      'ถาม KK AI ได้เลย',
      'ถามเรื่องสินค้าและภาพรวมร้านจากข้อมูลที่คุณมีสิทธิ์ดู',
    ),
  ];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xffe3eeff), Color(0xfff9fbff), Colors.white],
        ),
      ),
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 16, 0),
              child: Row(
                children: [
                  Image.asset(
                    'assets/kongklang-logo.png',
                    width: 36,
                    height: 36,
                  ),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'KONGKLANG',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                        color: KkColors.navy,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: widget.onDone,
                    child: const Text('ข้าม'),
                  ),
                ],
              ),
            ),
            Expanded(
              child: PageView.builder(
                controller: _controller,
                itemCount: _pages.length,
                onPageChanged: (index) => setState(() => _page = index),
                itemBuilder: (context, index) {
                  final (icon, title, description) = _pages[index];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(24, 20, 24, 18),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(
                          child: Center(
                            child: AspectRatio(
                              aspectRatio: 1.1,
                              child: GlassContainer(
                                radius: 35,
                                child: Stack(
                                  alignment: Alignment.center,
                                  children: [
                                    Positioned(
                                      right: -24,
                                      top: -24,
                                      child: CircleAvatar(
                                        radius: 105,
                                        backgroundColor: const Color(
                                          0x552563eb,
                                        ),
                                      ),
                                    ),
                                    Icon(icon, size: 110, color: KkColors.blue),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 28),
                        Text(
                          title,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: KkColors.navy,
                            fontSize: 28,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 9),
                        Text(
                          description,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: KkColors.muted,
                            height: 1.5,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 6, 24, 22),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(
                      _pages.length,
                      (index) => AnimatedContainer(
                        duration: MediaQuery.disableAnimationsOf(context)
                            ? Duration.zero
                            : const Duration(milliseconds: 250),
                        margin: const EdgeInsets.symmetric(horizontal: 3),
                        width: index == _page ? 22 : 7,
                        height: 7,
                        decoration: BoxDecoration(
                          color: index == _page
                              ? KkColors.blue
                              : const Color(0xffb9cbed),
                          borderRadius: BorderRadius.circular(5),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: () {
                        if (_page == _pages.length - 1) {
                          widget.onDone();
                        } else {
                          _controller.nextPage(
                            duration: MediaQuery.disableAnimationsOf(context)
                                ? Duration.zero
                                : const Duration(milliseconds: 320),
                            curve: Curves.easeOutCubic,
                          );
                        }
                      },
                      child: Text(
                        _page == _pages.length - 1 ? 'เริ่มใช้งาน' : 'ถัดไป',
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
