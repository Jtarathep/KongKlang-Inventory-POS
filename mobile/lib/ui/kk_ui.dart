import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Shared visual language for the mobile workspace. Data and permissions remain
/// owned by the existing API and screen state.
abstract final class KkColors {
  static const navy = Color(0xff132748);
  static const blue = Color(0xff2563eb);
  static const pale = Color(0xffedf3ff);
  static const ink = Color(0xff17243b);
  static const muted = Color(0xff58677d);
  static const canvas = Color(0xfff5f8fd);
}

class GlassContainer extends StatelessWidget {
  const GlassContainer({
    super.key,
    required this.child,
    this.padding = EdgeInsets.zero,
    this.radius = 22,
    this.floating = false,
    this.dark = false,
    this.tint,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final bool floating;
  final bool dark;
  final Color? tint;

  @override
  Widget build(BuildContext context) {
    final highContrast = MediaQuery.highContrastOf(context);
    final shape = BorderRadius.circular(radius);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: shape,
        boxShadow: [
          BoxShadow(
            color: const Color(0xff173a6e)
                .withValues(alpha: floating ? .16 : .07),
            blurRadius: floating ? 24 : 15,
            offset: Offset(0, floating ? 9 : 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: shape,
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          enabled: !highContrast,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: highContrast
                  ? (dark ? KkColors.navy : Colors.white)
                  : dark
                  ? const Color(0xcc173b75)
                  : tint ?? const Color(0xedffffff),
              borderRadius: shape,
              border: Border.all(
                color: dark ? const Color(0x88ffffff) : const Color(0xffdce8fa),
              ),
            ),
            child: Material(
              type: MaterialType.transparency,
              child: Padding(padding: padding, child: child),
            ),
          ),
        ),
      ),
    );
  }
}

class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.onTap,
  });
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => GlassContainer(
    child: onTap == null
        ? Padding(padding: padding, child: child)
        : InkWell(
            borderRadius: BorderRadius.circular(20),
            onTap: onTap,
            child: Padding(padding: padding, child: child),
          ),
  );
}

class GlassButton extends StatelessWidget {
  const GlassButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.primary = false,
  });
  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool primary;

  @override
  Widget build(BuildContext context) => primary
      ? FilledButton.icon(
          onPressed: onPressed,
          icon: Icon(icon),
          label: Text(label),
        )
      : OutlinedButton.icon(
          onPressed: onPressed,
          icon: Icon(icon),
          label: Text(label),
          style: OutlinedButton.styleFrom(
            backgroundColor: const Color(0xe6ffffff),
            foregroundColor: KkColors.blue,
            side: const BorderSide(color: Color(0xffd5e4fa)),
            shape: const StadiumBorder(),
          ),
        );
}

class GlassInput extends StatelessWidget {
  const GlassInput({
    super.key,
    required this.controller,
    required this.label,
    this.icon = Icons.search_rounded,
    this.onSubmitted,
  });
  final TextEditingController controller;
  final String label;
  final IconData icon;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) => TextField(
    controller: controller,
    maxLength: 120,
    textInputAction: TextInputAction.search,
    onSubmitted: onSubmitted,
    decoration: InputDecoration(
      hintText: label,
      counterText: '',
      prefixIcon: Icon(icon),
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 15),
      border: const OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(30)),
      ),
      enabledBorder: const OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(30)),
        borderSide: BorderSide(color: Color(0xffd3e3f8)),
      ),
    ),
  );
}

class KkPageHero extends StatelessWidget {
  const KkPageHero({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    this.compact = false,
    this.child,
  });
  final String title;
  final String subtitle;
  final IconData icon;
  final bool compact;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return Semantics(
      header: true,
      child: Container(
        constraints: BoxConstraints(minHeight: compact ? 132 : 188),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(28),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xff123971), Color(0xff2563eb), Color(0xff69a5f8)],
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x242563eb),
              blurRadius: 24,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: Stack(
          children: [
            Positioned(
              right: -30,
              top: -42,
              child: AnimatedRotation(
                turns: reduceMotion ? 0 : .045,
                duration: const Duration(milliseconds: 750),
                curve: Curves.easeOutCubic,
                child: Container(
                  width: compact ? 154 : 196,
                  height: compact ? 154 : 196,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: const Color(0x66ffffff),
                      width: 22,
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              right: 25,
              bottom: 4,
              child: Icon(
                icon,
                size: compact ? 82 : 110,
                color: const Color(0x42ffffff),
              ),
            ),
            Padding(
              padding: EdgeInsets.all(compact ? 21 : 25),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: compact ? 25 : 34,
                      height: 1.2,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 6),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 290),
                    child: Text(
                      subtitle,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        height: 1.5,
                      ),
                    ),
                  ),
                  if (child != null) ...[const SizedBox(height: 15), child!],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class KkFeaturedCarousel extends StatelessWidget {
  const KkFeaturedCarousel({super.key, required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final cardWidth = width < 360
        ? 222.0
        : width < 600
        ? 258.0
        : 300.0;
    return SizedBox(
      height: 142,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        itemCount: children.length,
        separatorBuilder: (_, _) => const SizedBox(width: 12),
        itemBuilder: (context, index) =>
            SizedBox(width: cardWidth, child: children[index]),
      ),
    );
  }
}

class KkProductCard extends StatelessWidget {
  const KkProductCard({
    super.key,
    required this.product,
    required this.onTap,
    this.compact = false,
  });
  final Map product;
  final VoidCallback onTap;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final quantity = (product['quantity'] as num? ?? 0).toInt();
    final low = quantity <= 5;
    final price = product['price'] as num?;
    return GlassContainer(
      radius: 20,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: compact
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _thumbnail(low),
                    const Spacer(),
                    Text(
                      '${product['name']}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                    Text(
                      '${product['code']}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '$quantity หน่วย${price == null ? '' : ' · ฿${price.toStringAsFixed(2)}'}',
                      style: TextStyle(
                        color: low ? const Color(0xff946000) : KkColors.blue,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                )
              : Row(
                  children: [
                    _thumbnail(low),
                    const SizedBox(width: 13),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${product['name']}',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 15,
                            ),
                          ),
                          Text(
                            '${product['code']} · ${product['category'] ?? 'ไม่ระบุหมวด'}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              color: KkColors.muted,
                            ),
                          ),
                          if (price != null)
                            Text(
                              '฿${price.toStringAsFixed(2)}',
                              style: const TextStyle(
                                color: KkColors.blue,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 7),
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '$quantity',
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 20,
                          ),
                        ),
                        Text(
                          low
                              ? (quantity == 0 ? 'หมดสต็อก' : 'ใกล้หมด')
                              : 'หน่วย',
                          style: TextStyle(
                            color: low
                                ? const Color(0xff946000)
                                : KkColors.muted,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
        ),
      ),
    );
  }

  Widget _thumbnail(bool low) {
    final id = product['id'];
    if (id == null) return _icon(low);
    return ClipRRect(
      borderRadius: BorderRadius.circular(15),
      child: Image.network(
        Uri.base.resolve('/products/$id/image').toString(),
        width: 48,
        height: 48,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => _icon(low),
      ),
    );
  }

  Widget _icon(bool low) => Container(
    width: 48,
    height: 48,
    decoration: BoxDecoration(
      color: low ? const Color(0xfffff1e1) : KkColors.pale,
      borderRadius: BorderRadius.circular(15),
    ),
    child: Icon(
      Icons.inventory_2_rounded,
      color: low ? const Color(0xff946000) : KkColors.blue,
    ),
  );
}

class GlassChatBubble extends StatelessWidget {
  const GlassChatBubble({super.key, required this.child, this.mine = false});
  final Widget child;
  final bool mine;

  @override
  Widget build(BuildContext context) => GlassContainer(
    radius: 18,
    padding: const EdgeInsets.all(14),
    tint: mine ? const Color(0xffe2eeff) : null,
    child: child,
  );
}

class GlassDialog extends StatelessWidget {
  const GlassDialog({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => Dialog(
    backgroundColor: Colors.transparent,
    child: GlassContainer(
      floating: true,
      padding: const EdgeInsets.all(20),
      child: child,
    ),
  );
}

class GlassBottomSheet extends StatelessWidget {
  const GlassBottomSheet({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => GlassContainer(
    floating: true,
    radius: 28,
    padding: const EdgeInsets.all(18),
    child: child,
  );
}
