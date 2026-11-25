import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:js_interop';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:web/web.dart' as web;

import 'services/api_service.dart';
import 'ui/kk_ui.dart';
import 'ui/onboarding.dart';

@JS('kongklangScanBarcode')
external JSPromise<JSObject> scanBarcode();

extension type CameraResult(JSObject _) implements JSObject {
  external String? get value;
  external String? get error;
}

void main() {
  final current = Uri.base;
  final isLoopback = const {
    'localhost',
    '127.0.0.1',
    '::1',
  }.contains(current.host);
  if (kDebugMode && isLoopback && current.port != 8080) {
    final demo = current.replace(
      scheme: 'http',
      port: 8080,
      path: '/mobile',
      query: '',
      fragment: '',
    );
    web.window.location.replace(demo.toString());
    return;
  }
  runApp(const KongklangApp());
}

class KongklangApp extends StatelessWidget {
  const KongklangApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'คงคลัง | KONGKLANG',
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff2563eb))
          .copyWith(
            primary: const Color(0xff2563eb),
            surface: Colors.white,
            onSurface: const Color(0xff17243b),
            secondaryContainer: const Color(0xffedf3ff),
          ),
      scaffoldBackgroundColor: KkColors.canvas,
      fontFamily: 'NotoSansThai',
      textTheme: const TextTheme(
        headlineSmall: TextStyle(
          fontSize: 30,
          fontWeight: FontWeight.w800,
          height: 1.22,
        ),
        titleMedium: TextStyle(
          fontSize: 19,
          fontWeight: FontWeight.w800,
          height: 1.3,
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xfff9fbff),
        surfaceTintColor: Colors.transparent,
        foregroundColor: Color(0xff17243b),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xd9ffffff),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xffdfe6ef)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(0, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      navigationBarTheme: const NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: Color(0xffedf3ff),
        elevation: 0,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: Color(0xffdfe6ef)),
        ),
      ),
    ),
    home: const Workspace(),
  );
}

class Workspace extends StatefulWidget {
  const Workspace({super.key});
  @override
  State<Workspace> createState() => _WorkspaceState();
}

class _WorkspaceState extends State<Workspace> {
  final api = ApiService();
  final search = TextEditingController();
  late Future<Map<String, dynamic>> session;
  Future<dynamic>? data;
  Timer? liveTimer;
  bool checkingUpdates = false;
  bool reconnecting = false;
  DateTime? syncedAt;
  String tab = 'dashboard';
  String submittedQuery = '';
  String stockFilter = 'all';
  bool productGrid = false;
  final stockFilters = const {
    'all': 'ทั้งหมด',
    'replenish': 'ต้องเติม',
    'low': 'ใกล้หมด',
    'out': 'หมดสต็อก',
  };
  bool signingOut = false;
  bool assistantOpened = false;
  bool onboardingDone = false;
  final labels = const {
    'dashboard': 'หน้าหลัก',
    'products': 'สินค้า',
    'ai': 'KK AI',
    'receive': 'คลังสินค้า',
    'settings': 'ตั้งค่า',
  };
  final icons = const {
    'dashboard': Icons.home_rounded,
    'products': Icons.inventory_2_rounded,
    'ai': Icons.auto_awesome,
    'receive': Icons.warehouse_rounded,
    'settings': Icons.settings_rounded,
  };
  @override
  void initState() {
    super.initState();
    try {
      onboardingDone =
          web.window.localStorage.getItem('kk.mobile.onboarding.v1') == 'done';
    } catch (_) {
      // Private browsing can disable storage; this visit can still continue.
    }
    session = loadSession();
    liveTimer = Timer.periodic(const Duration(seconds: 4), (_) {
      unawaited(checkForUpdates());
    });
  }

  Future<Map<String, dynamic>> loadSession() async {
    final value = Map<String, dynamic>.from(await api.get('session') as Map);
    if (mounted) {
      setState(() {
        syncedAt = DateTime.now();
        reconnecting = false;
        data = loadData();
      });
    }
    return value;
  }

  Future<dynamic>? loadData() => switch (tab) {
    'products' => api.get(tab, {'query': submittedQuery, 'stock': stockFilter}),
    'dashboard' => api.get(tab),
    _ => null,
  };
  Future<void> checkForUpdates() async {
    final current = data;
    if (current == null || checkingUpdates || web.document.hidden) return;
    checkingUpdates = true;
    try {
      dynamic previous;
      try {
        previous = await current;
      } catch (_) {
        // A later poll can recover an unsuccessful initial request.
      }
      if (!mounted || !identical(data, current) || web.document.hidden) return;
      final latest = await loadData();
      if (mounted && identical(data, current)) {
        final changed = jsonEncode(previous) != jsonEncode(latest);
        setState(() {
          syncedAt = DateTime.now();
          reconnecting = false;
          if (changed) data = Future.value(latest);
        });
      }
    } on ApiFailure catch (error) {
      if (error.status == 401 && mounted && identical(data, current)) {
        open('/login');
      } else if (mounted && identical(data, current) && !reconnecting) {
        setState(() => reconnecting = true);
      }
    } catch (_) {
      if (mounted && identical(data, current) && !reconnecting) {
        setState(() => reconnecting = true);
      }
    } finally {
      checkingUpdates = false;
    }
  }

  void refresh() => setState(() {
    submittedQuery = search.text.trim();
    data = loadData();
  });
  void select(String value) => setState(() {
    if (tab == value) return;
    tab = value;
    assistantOpened |= value == 'ai';
    data = loadData();
  });
  void showProducts(String filter) => setState(() {
    search.clear();
    submittedQuery = '';
    stockFilter = filter;
    tab = 'products';
    data = loadData();
  });

  void open(String path) =>
      web.window.location.assign(path == '/login' ? '/login?mobile=1' : path);

  void finishOnboarding() {
    try {
      web.window.localStorage.setItem('kk.mobile.onboarding.v1', 'done');
    } catch (_) {
      // Keep the authenticated workspace available for this visit.
    }
    setState(() => onboardingDone = true);
  }

  Future<void> logout(String csrf) async {
    if (signingOut) return;
    setState(() => signingOut = true);
    try {
      await api.logout(csrf);
      if (mounted) web.window.location.replace('/login?mobile=1');
    } catch (_) {
      if (!mounted) return;
      setState(() => signingOut = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('ออกจากระบบไม่สำเร็จ กรุณาลองใหม่')),
      );
    }
  }

  @override
  void dispose() {
    liveTimer?.cancel();
    search.dispose();
    api.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: session,
    builder: (context, state) {
      if (state.connectionState != ConnectionState.done) {
        return const KkSplash();
      }
      if (state.hasError) {
        return Scaffold(
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('${state.error}'),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: () => open('/login'),
                    child: const Text('เข้าสู่ระบบ KONGKLANG'),
                  ),
                  TextButton(
                    onPressed: () => setState(() => session = loadSession()),
                    child: const Text('ลองใหม่'),
                  ),
                ],
              ),
            ),
          ),
        );
      }
      final shop = state.requireData;
      if (!onboardingDone) {
        return KkOnboarding(onDone: finishOnboarding);
      }
      final destinations = [
        'dashboard',
        'products',
        'ai',
        'receive',
        'settings',
      ];
      return LayoutBuilder(
        builder: (context, layout) {
          final wide = layout.maxWidth >= 850;
          return Scaffold(
            extendBody: !wide && (tab == 'dashboard' || tab == 'products'),
            appBar: AppBar(
              toolbarHeight: 70,
              titleSpacing: 20,
              title: SizedBox(
                height: 62,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Image.asset(
                        'assets/kongklang-logo.png',
                        width: 42,
                        height: 42,
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 52),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            'KONGKLANG',
                            style: TextStyle(
                              fontSize: 19,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          Text(
                            '${shop['store']}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              color: Color(0xff58677d),
                            ),
                          ),
                          if (syncedAt != null)
                            Text(
                              reconnecting
                                  ? 'กำลังเชื่อมต่อใหม่'
                                  : 'ซิงก์ล่าสุด ${TimeOfDay.fromDateTime(syncedAt!).format(context)}',
                              maxLines: 1,
                              style: TextStyle(
                                fontSize: 10,
                                color: reconnecting
                                    ? const Color(0xff814b00)
                                    : const Color(0xff386254),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            bottomNavigationBar:
                wide || MediaQuery.viewInsetsOf(context).bottom > 0
                ? null
                : SafeArea(
                    minimum: const EdgeInsets.fromLTRB(14, 4, 14, 12),
                    child: GlassNavigation(
                      labels: destinations.map((key) => labels[key]!).toList(),
                      icons: destinations.map((key) => icons[key]!).toList(),
                      selected: destinations.indexOf(tab),
                      onSelect: (index) => select(destinations[index]),
                    ),
                  ),
            body: DecoratedBox(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Color(0xfff9fbff),
                    Color(0xffedf3ff),
                    Color(0xfff5f8fd),
                  ],
                  stops: [0, .55, 1],
                ),
              ),
              child: Row(
                children: [
                  if (wide)
                    NavigationRail(
                      extended: true,
                      selectedIndex: destinations.indexOf(tab),
                      onDestinationSelected: (i) => select(destinations[i]),
                      destinations: [
                        for (final d in destinations)
                          NavigationRailDestination(
                            icon: Icon(icons[d]),
                            label: Text(labels[d]!),
                          ),
                      ],
                    ),
                  Expanded(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 1000),
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            if (tab != 'ai' || shop['canAnalyze'] != true)
                              body(shop),
                            if (assistantOpened && shop['canAnalyze'] == true)
                              Visibility(
                                key: const ValueKey('assistant'),
                                visible: tab == 'ai',
                                maintainState: true,
                                child: MobileAssistant(
                                  api: api,
                                  csrf: shop['csrf'].toString(),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      );
    },
  );

  Widget body(Map<String, dynamic> shop) {
    final csrf = shop['csrf'].toString();
    if (tab == 'settings') {
      return ListView(
        padding: EdgeInsets.fromLTRB(
          20,
          18,
          20,
          130 + MediaQuery.paddingOf(context).bottom,
        ),
        children: [
          const KkPageHero(
            title: 'ตั้งค่า',
            subtitle: 'บัญชีและร้านที่กำลังใช้งาน',
            icon: Icons.tune_rounded,
            compact: true,
          ),
          const SizedBox(height: 16),
          GlassPanel(
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 18,
                vertical: 9,
              ),
              leading: const CircleAvatar(
                backgroundColor: Color(0xffedf3ff),
                foregroundColor: Color(0xff2563eb),
                child: Icon(Icons.person_rounded),
              ),
              title: Text(
                shop['displayName'].toString(),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Text(shop['store'].toString()),
            ),
          ),
          const SizedBox(height: 16),
          GlassCard(
            onTap: () => open('/settings/password'),
            child: const Row(
              children: [
                Icon(Icons.lock_outline_rounded, color: KkColors.blue),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'เปลี่ยนรหัสผ่าน',
                    style: TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                Icon(Icons.chevron_right_rounded, color: KkColors.muted),
              ],
            ),
          ),
          if (shop['role'] == 'OWNER' || shop['role'] == 'ADMIN') ...[
            const SizedBox(height: 12),
            GlassCard(
              onTap: () => open('/management/users'),
              child: const Row(
                children: [
                  Icon(Icons.people_outline_rounded, color: KkColors.blue),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'จัดการผู้ใช้งาน',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded, color: KkColors.muted),
                ],
              ),
            ),
          ],
          const SizedBox(height: 20),
          FilledButton.tonalIcon(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xffffedf0),
              foregroundColor: const Color(0xffb42335),
            ),
            onPressed: signingOut ? null : () => logout(csrf),
            icon: const Icon(Icons.logout),
            label: Text(signingOut ? 'กำลังออกจากระบบ…' : 'ออกจากระบบ'),
          ),
        ],
      );
    }
    if (tab == 'ai') {
      return const Center(child: Text('บัญชีนี้ไม่มีสิทธิ์ใช้ KK AI'));
    }
    if (tab == 'receive') {
      if (shop['canReceive'] != true) {
        return Center(
          child: TextButton(
            onPressed: () => select('products'),
            child: const Text('ดูสินค้าในคลัง'),
          ),
        );
      }
      return StockWorkspace(
        api: api,
        csrf: shop['csrf'].toString(),
        account: '${shop['store']}.${shop['displayName']}',
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (tab == 'products')
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const KkPageHero(
                  title: 'สินค้า',
                  subtitle: 'ค้นหาและตรวจสอบสินค้าของร้าน',
                  icon: Icons.inventory_2_rounded,
                  compact: true,
                ),
                const SizedBox(height: 16),
                if (shop['role'] == 'OWNER' || shop['role'] == 'ADMIN') ...[
                  GlassButton(
                    label: 'เพิ่มสินค้า',
                    icon: Icons.add_rounded,
                    onPressed: () => open('/products/new'),
                  ),
                  const SizedBox(height: 12),
                ],
                Row(
                  children: [
                    Expanded(
                      child: GlassInput(
                        controller: search,
                        label: 'ชื่อสินค้า รหัส หรือบาร์โค้ด',
                        onSubmitted: (_) => refresh(),
                      ),
                    ),
                    const SizedBox(width: 10),
                    IconButton.filledTonal(
                      onPressed: refresh,
                      icon: const Icon(Icons.search),
                      tooltip: 'ค้นหา',
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      for (final entry in stockFilters.entries)
                        Padding(
                          padding: const EdgeInsets.only(right: 7),
                          child: ChoiceChip(
                            label: Text(entry.value),
                            selected: stockFilter == entry.key,
                            showCheckmark: false,
                            backgroundColor: const Color(0x99ffffff),
                            selectedColor: const Color(0xffd3e3ff),
                            shape: const StadiumBorder(),
                            onSelected: (_) {
                              if (stockFilter == entry.key) return;
                              setState(() {
                                stockFilter = entry.key;
                                data = loadData();
                              });
                            },
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        Expanded(
          child: FutureBuilder<dynamic>(
            key: ValueKey(tab),
            future: data,
            builder: (context, snapshot) {
              if (snapshot.connectionState != ConnectionState.done &&
                  !snapshot.hasData) {
                return const LoadingLayout();
              }
              if (snapshot.hasError) {
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('${snapshot.error}'),
                        const SizedBox(height: 12),
                        OutlinedButton(
                          onPressed: refresh,
                          child: const Text('โหลดอีกครั้ง'),
                        ),
                        if (snapshot.error is ApiFailure &&
                            (snapshot.error as ApiFailure).status == 401)
                          TextButton(
                            onPressed: () => open('/login'),
                            child: const Text('เข้าสู่ระบบใหม่'),
                          ),
                      ],
                    ),
                  ),
                );
              }
              if (tab == 'dashboard') {
                final counts = snapshot.data as Map;
                return Dashboard(
                  counts: counts,
                  store: shop['store'].toString(),
                  displayName: shop['displayName'].toString(),
                  onProducts: () => showProducts('all'),
                  onLowStock: () => showProducts('replenish'),
                  onReceive: shop['canReceive'] == true
                      ? () => select('receive')
                      : null,
                  onPos: shop['canSeeSales'] == true
                      ? () => open('/sales')
                      : null,
                  onReports: shop['canAnalyze'] == true
                      ? () => open('/reports/overview')
                      : null,
                );
              }
              final rows = snapshot.data as List;
              if (rows.isEmpty) {
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: GlassPanel(
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.inventory_2_outlined,
                              color: Color(0xff2563eb),
                            ),
                            const SizedBox(height: 10),
                            const Text(
                              'ไม่พบสินค้าที่ตรงกับการค้นหาหรือตัวกรอง',
                            ),
                            TextButton(
                              onPressed: () => showProducts('all'),
                              child: const Text('ดูสินค้าทั้งหมด'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              }
              return LayoutBuilder(
                builder: (context, constraints) {
                  final columns = constraints.maxWidth >= 780 ? 3 : 2;
                  final bottom = 130 + MediaQuery.paddingOf(context).bottom;
                  return CustomScrollView(
                    slivers: [
                      SliverPadding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                        sliver: SliverToBoxAdapter(
                          child: Column(
                            children: [
                              GlassPanel(
                                child: Padding(
                                  padding: const EdgeInsets.all(16),
                                  child: Row(
                                    children: [
                                      Expanded(
                                        child: summaryValue(
                                          'รายการที่พบ',
                                          rows.length,
                                        ),
                                      ),
                                      Expanded(
                                        child: summaryValue(
                                          'คงเหลือในรายการที่พบ',
                                          rows.fold<int>(
                                            0,
                                            (total, row) =>
                                                total +
                                                ((row as Map)['quantity']
                                                            as num? ??
                                                        0)
                                                    .toInt(),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: 24),
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      'รายการสินค้า',
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleMedium,
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: productGrid
                                        ? 'แสดงแบบรายการ'
                                        : 'แสดงแบบตาราง',
                                    onPressed: () => setState(
                                      () => productGrid = !productGrid,
                                    ),
                                    icon: Icon(
                                      productGrid
                                          ? Icons.view_list_rounded
                                          : Icons.grid_view_rounded,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 10),
                            ],
                          ),
                        ),
                      ),
                      SliverPadding(
                        padding: EdgeInsets.fromLTRB(20, 0, 20, bottom),
                        sliver: productGrid
                            ? SliverGrid(
                                delegate: SliverChildBuilderDelegate((
                                  context,
                                  index,
                                ) {
                                  final row = rows[index] as Map;
                                  return KkProductCard(
                                    product: row,
                                    compact: true,
                                    onTap: () =>
                                        showProductDetails(context, row),
                                  );
                                }, childCount: rows.length),
                                gridDelegate:
                                    SliverGridDelegateWithFixedCrossAxisCount(
                                      crossAxisCount: columns,
                                      crossAxisSpacing: 10,
                                      mainAxisSpacing: 10,
                                      mainAxisExtent: 190,
                                    ),
                              )
                            : SliverList.separated(
                                itemCount: rows.length,
                                separatorBuilder: (_, _) =>
                                    const SizedBox(height: 10),
                                itemBuilder: (context, index) {
                                  final row = rows[index] as Map;
                                  return KkProductCard(
                                    product: row,
                                    onTap: () =>
                                        showProductDetails(context, row),
                                  );
                                },
                              ),
                      ),
                    ],
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }
}

Future<void> showProductDetails(BuildContext context, Map row) =>
    showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      showDragHandle: false,
      backgroundColor: Colors.transparent,
      builder: (context) => SafeArea(
        top: false,
        minimum: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        child: GlassPanel(
          radius: 28,
          floating: true,
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(18),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 34,
                    height: 4,
                    decoration: BoxDecoration(
                      color: const Color(0xffb2c0d6),
                      borderRadius: BorderRadius.circular(6),
                    ),
                  ),
                ),
                if (row['id'] != null) ...[
                  const SizedBox(height: 16),
                  Center(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: Image.network(
                        Uri.base
                            .resolve('/products/${row['id']}/image')
                            .toString(),
                        width: 96,
                        height: 96,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => const Icon(
                          Icons.inventory_2_rounded,
                          color: KkColors.blue,
                          size: 64,
                        ),
                      ),
                    ),
                  ),
                ],
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        row['name'].toString(),
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    IconButton(
                      onPressed: () => Navigator.pop(context),
                      tooltip: 'ปิดรายละเอียดสินค้า',
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ],
                ),
                for (final (name, value) in [
                  ('รหัสสินค้า', row['code']),
                  ('บาร์โค้ด', row['barcode']),
                  ('หมวดหมู่', row['category']),
                  ('คงเหลือ', '${row['quantity']} หน่วย'),
                ])
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 7),
                    child: Row(
                      children: [
                        Expanded(child: Text(name)),
                        const SizedBox(width: 10),
                        Expanded(
                          child: SelectableText(
                            value == null || value.toString().trim().isEmpty
                                ? '—'
                                : value.toString(),
                            textAlign: TextAlign.end,
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ),
                      ],
                    ),
                  ),
                if (row['id'] != null) ...[
                  const SizedBox(height: 12),
                  GlassButton(
                    label: 'รายละเอียดและจัดการสินค้า',
                    icon: Icons.open_in_new_rounded,
                    onPressed: () => web.window.location.assign(
                      '/products/${row['id']}/details',
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );

Duration motionDuration(BuildContext context) =>
    MediaQuery.disableAnimationsOf(context)
    ? Duration.zero
    : const Duration(milliseconds: 220);

class GlassNavigation extends StatelessWidget {
  const GlassNavigation({
    super.key,
    required this.labels,
    required this.icons,
    required this.selected,
    required this.onSelect,
  });
  final List<String> labels;
  final List<IconData> icons;
  final int selected;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final duration = motionDuration(context);
    return SizedBox(
      height: 88,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fill(
            top: 16,
            child: GlassPanel(
              radius: 32,
              floating: true,
              child: Row(
                children: List.generate(labels.length, (index) {
                  if (index == 2) return const Expanded(child: SizedBox());
                  final active = selected == index;
                  return Expanded(
                    child: Semantics(
                      selected: active,
                      child: PressFeedback(
                        onTap: () => onSelect(index),
                        radius: 24,
                        child: AnimatedContainer(
                          duration: duration,
                          margin: const EdgeInsets.all(5),
                          decoration: BoxDecoration(
                            color: active
                                ? const Color(0xcce0edff)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(24),
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                icons[index],
                                size: 23,
                                color: active
                                    ? const Color(0xff1855c6)
                                    : const Color(0xff58677d),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                labels[index],
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
          Align(
            alignment: Alignment.topCenter,
            child: Semantics(
              selected: selected == 2,
              label: labels[2],
              button: true,
              child: PressFeedback(
                onTap: () => onSelect(2),
                radius: 28,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    AnimatedContainer(
                      duration: duration,
                      width: 58,
                      height: 58,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: selected == 2
                              ? const [Color(0xff2589ff), Color(0xff174cce)]
                              : const [Color(0xff438fff), Color(0xff2563eb)],
                        ),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: const Color(0xe6ffffff),
                          width: 3,
                        ),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x35244891),
                            blurRadius: 16,
                            offset: Offset(0, 6),
                          ),
                        ],
                      ),
                      child: Icon(icons[2], color: Colors.white, size: 27),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      labels[2],
                      maxLines: 1,
                      style: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: Color(0xff174c9e),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.radius = 24,
    this.floating = false,
  });
  final Widget child;
  final double radius;
  final bool floating;

  @override
  Widget build(BuildContext context) =>
      GlassContainer(radius: radius, floating: floating, child: child);
}

class StockActionButton extends StatelessWidget {
  const StockActionButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.emphasis = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool emphasis;

  @override
  Widget build(BuildContext context) => GlassPanel(
    radius: 16,
    child: FilledButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 20),
      label: Text(label),
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 48),
        backgroundColor: emphasis
            ? const Color(0xffd3e3ff)
            : const Color(0x99edf4ff),
        foregroundColor: const Color(0xff164baf),
        disabledBackgroundColor: const Color(0x557587a4),
        disabledForegroundColor: const Color(0xff495b76),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
    ),
  );
}

class PressFeedback extends StatefulWidget {
  const PressFeedback({
    super.key,
    required this.onTap,
    required this.radius,
    required this.child,
  });
  final VoidCallback? onTap;
  final double radius;
  final Widget child;

  @override
  State<PressFeedback> createState() => _PressFeedbackState();
}

class _PressFeedbackState extends State<PressFeedback> {
  bool pressed = false;

  @override
  Widget build(BuildContext context) {
    final duration = motionDuration(context);
    return InkWell(
      onTap: widget.onTap,
      onHighlightChanged: widget.onTap == null
          ? null
          : (value) => setState(() => pressed = value),
      borderRadius: BorderRadius.circular(widget.radius),
      child: AnimatedScale(
        duration: duration == Duration.zero
            ? duration
            : const Duration(milliseconds: 105),
        scale: pressed && duration != Duration.zero ? .96 : 1,
        child: widget.child,
      ),
    );
  }
}

class LoadingLayout extends StatefulWidget {
  const LoadingLayout({super.key});

  @override
  State<LoadingLayout> createState() => _LoadingLayoutState();
}

class _LoadingLayoutState extends State<LoadingLayout>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      controller.stop();
      controller.value = .5;
    } else {
      controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  Widget placeholder(double height, {double? width}) => Container(
    height: height,
    width: width,
    decoration: BoxDecoration(
      color: const Color(0xffdbe5f3),
      borderRadius: BorderRadius.circular(18),
    ),
  );

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'กำลังโหลดข้อมูลร้าน',
    liveRegion: true,
    child: ExcludeSemantics(
      child: FadeTransition(
        opacity: Tween<double>(begin: .45, end: .9).animate(controller),
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            placeholder(22, width: 140),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(child: placeholder(126)),
                const SizedBox(width: 12),
                Expanded(child: placeholder(126)),
              ],
            ),
            const SizedBox(height: 20),
            placeholder(210),
            const SizedBox(height: 24),
            for (var i = 0; i < 3; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: 18),
                child: Row(
                  children: [
                    placeholder(52, width: 52),
                    const SizedBox(width: 14),
                    Expanded(child: placeholder(40)),
                  ],
                ),
              ),
          ],
        ),
      ),
    ),
  );
}

Widget summaryValue(String label, int value) => Column(
  crossAxisAlignment: CrossAxisAlignment.start,
  children: [
    Text(label, style: const TextStyle(color: Color(0xff58677d))),
    Text(
      '$value',
      style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800),
    ),
  ],
);

class MobileAssistant extends StatefulWidget {
  const MobileAssistant({super.key, required this.api, required this.csrf});

  final ApiService api;
  final String csrf;

  @override
  State<MobileAssistant> createState() => _MobileAssistantState();
}

class _MobileAssistantState extends State<MobileAssistant> {
  final input = TextEditingController();
  final messages = <(bool, String, String?)>[];
  bool busy = false;
  String? error;

  Future<void> ask() async {
    final question = input.text.trim();
    if (busy || question.isEmpty) return;
    final previousQuestion = messages
        .where((message) => message.$1)
        .firstOrNull
        ?.$2;
    setState(() {
      busy = true;
      error = null;
      messages.insert(0, (true, question, null));
    });
    try {
      final reply = await widget.api.ask(
        question,
        widget.csrf,
        previousQuestion: previousQuestion,
      );
      if (!mounted) return;
      final facts = (reply['facts'] as List?)?.map((fact) => '• $fact') ?? [];
      setState(() {
        input.clear();
        messages.insert(0, (
          false,
          [
            if (reply['title'] != null) reply['title'],
            if (reply['text'] != null) reply['text'],
            ...facts,
          ].join('\n\n'),
          reply['suggestion'] as String?,
        ));
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        messages.removeAt(0);
        error = 'ส่งคำถามไม่สำเร็จ กรุณาลองใหม่';
      });
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
        child: const KkPageHero(
          title: 'KK AI',
          subtitle: 'ถามข้อมูลสินค้าหรือภาพรวมร้านได้เลย',
          icon: Icons.auto_awesome_rounded,
          compact: true,
        ),
      ),
      Expanded(
        child: messages.isEmpty
            ? Center(
                child: SingleChildScrollView(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: GlassPanel(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 22,
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const CircleAvatar(
                              radius: 25,
                              backgroundColor: Color(0xffedf3ff),
                              foregroundColor: Color(0xff2563eb),
                              child: Icon(Icons.auto_awesome_rounded),
                            ),
                            const SizedBox(height: 14),
                            const Text('วันนี้อยากเช็กอะไรในร้าน?'),
                            const SizedBox(height: 10),
                            Wrap(
                              alignment: WrapAlignment.center,
                              spacing: 8,
                              children: [
                                for (final prompt in [
                                  'สินค้าในสต็อกมีอะไรบ้าง',
                                  'ควรซื้ออะไรเพิ่ม',
                                  'สินค้าใกล้หมดอายุ',
                                  'ยอดขายสุทธิ 7 วัน',
                                  'สินค้าขายดีที่สุด',
                                ])
                                  ActionChip(
                                    label: Text(prompt),
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 5,
                                    ),
                                    onPressed: () {
                                      input.text = prompt;
                                      ask();
                                    },
                                  ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              )
            : ListView.builder(
                reverse: true,
                padding: const EdgeInsets.all(20),
                itemCount: messages.length,
                itemBuilder: (context, index) {
                  final (mine, message, suggestion) = messages[index];
                  return Align(
                    alignment: mine
                        ? Alignment.centerRight
                        : Alignment.centerLeft,
                    child: GlassChatBubble(
                      mine: mine,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (mine)
                            SelectableText(message)
                          else ...[
                            SelectableText(
                              message.split('\n\n').first,
                              style: const TextStyle(
                                fontWeight: FontWeight.w800,
                                color: Color(0xff17243b),
                              ),
                            ),
                            for (final paragraph
                                in message.split('\n\n').skip(1))
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: SelectableText(
                                  paragraph,
                                  style: paragraph.startsWith('•')
                                      ? const TextStyle(
                                          fontWeight: FontWeight.w600,
                                          color: Color(0xff164baf),
                                        )
                                      : null,
                                ),
                              ),
                          ],
                          if (suggestion != null && suggestion.isNotEmpty) ...[
                            const Divider(height: 24),
                            const Text(
                              'คำแนะนำจาก AI',
                              style: TextStyle(
                                color: Color(0xff2563eb),
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            SelectableText(suggestion),
                          ],
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
      if (busy || error != null)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Text(error ?? 'กำลังอ่านข้อมูลร้าน…'),
        ),
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
        child: GlassPanel(
          radius: 22,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(9, 9, 9, 2),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: TextField(
                    controller: input,
                    enabled: !busy,
                    maxLength: 500,
                    decoration: const InputDecoration(
                      labelText: 'ถามเรื่องร้าน',
                    ),
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => ask(),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(
                  tooltip: 'ส่งคำถาม',
                  onPressed: busy ? null : ask,
                  icon: const Icon(Icons.send_rounded),
                ),
              ],
            ),
          ),
        ),
      ),
    ],
  );
}

class Dashboard extends StatelessWidget {
  const Dashboard({
    super.key,
    required this.counts,
    required this.store,
    required this.displayName,
    required this.onProducts,
    required this.onLowStock,
    this.onReceive,
    this.onPos,
    this.onReports,
  });

  final Map counts;
  final String store;
  final String displayName;
  final VoidCallback onProducts;
  final VoidCallback onLowStock;
  final VoidCallback? onReceive;
  final VoidCallback? onPos;
  final VoidCallback? onReports;

  @override
  Widget build(BuildContext context) {
    final days = (counts['salesDays'] as List?)?.cast<Map>() ?? const <Map>[];
    final stock =
        (counts['lowStockItems'] as List?)?.cast<Map>() ?? const <Map>[];
    return ListView(
      padding: EdgeInsets.fromLTRB(
        20,
        10,
        20,
        130 + MediaQuery.paddingOf(context).bottom,
      ),
      children: [
        KkPageHero(
          title: 'DASHBOARD',
          subtitle: 'สวัสดี $displayName · $store',
          icon: Icons.space_dashboard_rounded,
          child: const Text(
            'ภาพรวมร้านค้า อัปเดตอัตโนมัติ',
            style: TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        const SizedBox(height: 22),
        Text('ภาพรวมวันนี้', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: days.isEmpty
                  ? metric(
                      context,
                      'สินค้าใกล้หมด',
                      counts['lowStock'],
                      Icons.warning_amber_rounded,
                      null,
                    )
                  : metric(
                      context,
                      'ยอดขายสุทธิวันนี้',
                      '฿${(days.last['amount'] as num).toStringAsFixed(2)}',
                      Icons.bar_chart_rounded,
                      null,
                    ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: metric(
                context,
                'รายการสินค้า',
                counts['products'],
                Icons.inventory_2_rounded,
                onProducts,
              ),
            ),
          ],
        ),
        if (days.isNotEmpty) ...[
          const SizedBox(height: 14),
          SalesChart(days: days),
        ],
        const SizedBox(height: 22),
        Text('สิ่งที่ควรดู', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        KkFeaturedCarousel(
          children: [
            GlassCard(
              onTap: onLowStock,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Icon(
                    Icons.warning_amber_rounded,
                    color: Color(0xffb96a00),
                  ),
                  Text(
                    'สินค้าใกล้หมด',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text('${counts['lowStock']} รายการ · แตะเพื่อตรวจสอบ'),
                ],
              ),
            ),
            GlassCard(
              onTap: onProducts,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Icon(Icons.inventory_2_outlined, color: KkColors.blue),
                  Text(
                    'สินค้าในร้าน',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text('${counts['products']} รายการ · ดูสินค้าทั้งหมด'),
                ],
              ),
            ),
            if (onReceive != null)
              GlassCard(
                onTap: onReceive,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Icon(Icons.add_box_outlined, color: KkColors.blue),
                    Text(
                      'รับสินค้าเข้าคลัง',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const Text('เพิ่มสินค้าเข้าร้าน'),
                  ],
                ),
              ),
          ],
        ),
        const SizedBox(height: 22),
        GlassPanel(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'สินค้าใกล้หมด',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    IconButton.filledTonal(
                      onPressed: onLowStock,
                      tooltip: 'ดูสินค้าที่ต้องเติม',
                      icon: const Icon(Icons.arrow_forward),
                    ),
                  ],
                ),
                Text('${counts['lowStock']} รายการ · เหลือไม่เกิน 5 หน่วย'),
                if (stock.isEmpty && counts['lowStock'] == 0)
                  const Padding(
                    padding: EdgeInsets.only(top: 12),
                    child: Text('ยังไม่มีสินค้าที่ใกล้หมด'),
                  ),
                for (final item in stock)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const CircleAvatar(
                      backgroundColor: Color(0xffedf3ff),
                      foregroundColor: Color(0xff2563eb),
                      child: Icon(Icons.inventory_2_rounded, size: 18),
                    ),
                    title: Text(item['name'].toString()),
                    trailing: Text(
                      '${item['quantity']} หน่วย',
                      style: const TextStyle(color: Color(0xff946000)),
                    ),
                  ),
              ],
            ),
          ),
        ),
        if (onReceive != null) ...[
          const SizedBox(height: 14),
          StockActionButton(
            onPressed: onReceive,
            icon: Icons.inventory_2_outlined,
            label: 'รับสินค้าเข้าคลัง',
            emphasis: true,
          ),
        ],
        if (onPos != null || onReports != null) ...[
          const SizedBox(height: 20),
          Text('งานที่ทำบ่อย', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 10),
          Wrap(
            spacing: 9,
            runSpacing: 9,
            children: [
              if (onPos != null)
                GlassButton(
                  label: 'ขายสินค้า',
                  icon: Icons.point_of_sale_rounded,
                  onPressed: onPos,
                ),
              if (onReports != null)
                GlassButton(
                  label: 'ดูรายงาน',
                  icon: Icons.insights_rounded,
                  onPressed: onReports,
                ),
            ],
          ),
        ],
      ],
    );
  }

  Widget metric(
    BuildContext context,
    String label,
    Object? value,
    IconData icon,
    VoidCallback? action,
  ) {
    return GlassPanel(
      radius: 20,
      child: PressFeedback(
        radius: 20,
        onTap: action,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 15, 14, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(color: Color(0xff58677d))),
              const SizedBox(height: 6),
              Row(
                children: [
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: motionDuration(context),
                      layoutBuilder: (current, previous) => Stack(
                        alignment: Alignment.centerLeft,
                        children: [...previous, ?current],
                      ),
                      child: FittedBox(
                        key: ValueKey(value),
                        alignment: Alignment.centerLeft,
                        fit: BoxFit.scaleDown,
                        child: Text(
                          '$value',
                          style: const TextStyle(
                            fontSize: 31,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 5),
                  Icon(icon, color: const Color(0xff2563eb), size: 19),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class SalesChart extends StatefulWidget {
  const SalesChart({super.key, required this.days});
  final List<Map> days;

  @override
  State<SalesChart> createState() => _SalesChartState();
}

class _SalesChartState extends State<SalesChart> {
  int? selected;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final days = widget.days;
    final amounts = days
        .map((day) => (day['amount'] as num).toDouble())
        .toList();
    final costs = days.map((day) => (day['cost'] as num?)?.toDouble()).toList();
    final showCosts = costs.any((cost) => cost != null);
    final values = [...amounts, ...costs.whereType<double>()];
    final high = values.fold<double>(0, max);
    final low = values.fold<double>(0, min);
    final span = high == low ? 1.0 : high - low;
    const height = 174.0;
    final zero = high == low ? height : high / span * height;
    final lastSale = amounts.lastIndexWhere((amount) => amount != 0);
    final active = selected ?? (lastSale < 0 ? days.length - 1 : lastSale);
    final date = days[active]['date'].toString().substring(5);
    return GlassPanel(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'ยอดขายสุทธิ',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                DecoratedBox(
                  decoration: const ShapeDecoration(
                    color: Color(0xff2563eb),
                    shape: StadiumBorder(),
                  ),
                  child: const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    child: Text('7 วัน', style: TextStyle(color: Colors.white)),
                  ),
                ),
              ],
            ),
            AnimatedAlign(
              duration: motionDuration(context),
              curve: Curves.easeOutCubic,
              alignment: Alignment(
                days.length == 1 ? 0 : 2 * active / (days.length - 1) - 1,
                0,
              ),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: const Color(0xff17243b),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 7,
                  ),
                  child: Text(
                    '$date · ฿${amounts[active].toStringAsFixed(2)}'
                    '${costs[active] == null ? "" : "  ·  ต้นทุน ฿${costs[active]!.toStringAsFixed(2)}"}',
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                for (var i = 0; i < days.length; i++)
                  Expanded(
                    child: Semantics(
                      selected: i == active,
                      child: InkWell(
                        onTap: () => setState(() => selected = i),
                        child: Column(
                          children: [
                            SizedBox(
                              height: height,
                              child: Stack(
                                fit: StackFit.expand,
                                children: [
                                  for (final y in [0.0, height / 2, height - 1])
                                    Positioned(
                                      top: y,
                                      left: 0,
                                      right: 0,
                                      child: const Divider(height: 1),
                                    ),
                                  if (low < 0)
                                    Positioned(
                                      top: zero.clamp(0, height - 1).toDouble(),
                                      left: 0,
                                      right: 0,
                                      child: const Divider(height: 1),
                                    ),
                                  if (i == active)
                                    const Align(
                                      alignment: Alignment.center,
                                      child: SizedBox(
                                        width: 1,
                                        height: double.infinity,
                                        child: ColoredBox(
                                          color: Color(0x552563eb),
                                        ),
                                      ),
                                    ),
                                  Align(
                                    alignment: showCosts
                                        ? Alignment.centerLeft
                                        : Alignment.center,
                                    child: FractionallySizedBox(
                                      widthFactor: showCosts ? .43 : .72,
                                      child: Stack(
                                        fit: StackFit.expand,
                                        children: [
                                          bar(
                                            context,
                                            amounts[i],
                                            zero,
                                            span,
                                            height,
                                            amounts[i] < 0
                                                ? colors.error
                                                : i == active
                                                ? colors.primary
                                                : const Color(0xff6189ed),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                  if (costs[i] case final double cost)
                                    Align(
                                      alignment: Alignment.centerRight,
                                      child: FractionallySizedBox(
                                        widthFactor: .43,
                                        child: Stack(
                                          fit: StackFit.expand,
                                          children: [
                                            bar(
                                              context,
                                              cost,
                                              zero,
                                              span,
                                              height,
                                              const Color(0xffc5cfdf),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 6),
                            FittedBox(
                              child: Text(
                                days[i]['date'].toString().substring(5),
                                style: Theme.of(context).textTheme.labelSmall,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 14,
              runSpacing: 4,
              children: [
                legend('ยอดขายสุทธิ', colors.primary),
                if (showCosts) legend('ต้นทุนขาย', const Color(0xffc5cfdf)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget bar(
    BuildContext context,
    double amount,
    double zero,
    double span,
    double height,
    Color color,
  ) => AnimatedPositioned(
    duration: motionDuration(context),
    curve: Curves.easeOutCubic,
    top: max(0, amount > 0 ? zero - amount / span * height : zero),
    left: 2,
    right: 2,
    height: amount.abs() / span * height,
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(5),
      ),
    ),
  );

  Widget legend(String label, Color color) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      DecoratedBox(
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        child: const SizedBox.square(dimension: 9),
      ),
      const SizedBox(width: 5),
      Text(label, style: const TextStyle(fontSize: 12)),
    ],
  );
}

class StockWorkspace extends StatefulWidget {
  const StockWorkspace({
    super.key,
    required this.api,
    required this.csrf,
    required this.account,
  });
  final ApiService api;
  final String csrf;
  final String account;
  @override
  State<StockWorkspace> createState() => _StockWorkspaceState();
}

class _StockWorkspaceState extends State<StockWorkspace> {
  int selected = 0;
  Future<dynamic>? records;

  void select(int value) {
    setState(() {
      selected = value;
      records = value == 0
          ? null
          : widget.api.get(value == 1 ? 'batches' : 'movements');
    });
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      const Padding(
        padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
        child: KkPageHero(
          title: 'คลังสินค้า',
          subtitle: 'รับสินค้า ติดตามล็อต และดูประวัติการเคลื่อนไหว',
          icon: Icons.warehouse_rounded,
          compact: true,
        ),
      ),
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final (index, label) in [
                'รับสินค้า',
                'ล็อตสินค้า',
                'ประวัติ',
              ].indexed)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(label),
                    selected: selected == index,
                    showCheckmark: false,
                    shape: const StadiumBorder(),
                    onSelected: (_) => select(index),
                  ),
                ),
            ],
          ),
        ),
      ),
      Expanded(
        child: selected == 0
            ? ReceiveForm(
                api: widget.api,
                csrf: widget.csrf,
                account: widget.account,
              )
            : FutureBuilder<dynamic>(
                future: records,
                builder: (context, state) {
                  if (state.connectionState != ConnectionState.done) {
                    return const LoadingLayout();
                  }
                  if (state.hasError) {
                    return Center(
                      child: TextButton(
                        onPressed: () => select(selected),
                        child: const Text('เชื่อมต่อไม่ได้ · ลองใหม่'),
                      ),
                    );
                  }
                  final rows = state.data as List;
                  if (rows.isEmpty) {
                    return const Center(child: Text('ยังไม่มีรายการของร้าน'));
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.all(20),
                    itemCount: rows.length + 1,
                    separatorBuilder: (_, index) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return Text(
                          'แสดงรายการล่าสุด สูงสุด 100 รายการ',
                          style: Theme.of(context).textTheme.bodySmall,
                        );
                      }
                      final row = rows[index - 1] as Map;
                      final detail = selected == 1
                          ? 'คงเหลือ ${row['remaining']} · หมดอายุ ${row['expiry'] ?? 'ไม่ระบุ'}'
                          : '${row['type']} · จำนวน ${row['quantity']} · คงเหลือ ${row['balance']}';
                      return GlassPanel(
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 18,
                            vertical: 10,
                          ),
                          leading: Icon(
                            selected == 1
                                ? Icons.inventory_2_outlined
                                : Icons.history_rounded,
                            color: const Color(0xff2563eb),
                          ),
                          title: Text(
                            '${row['product']}',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          subtitle: Text(detail),
                        ),
                      );
                    },
                  );
                },
              ),
      ),
    ],
  );
}

class ReceiveForm extends StatefulWidget {
  const ReceiveForm({
    super.key,
    required this.api,
    required this.csrf,
    required this.account,
  });
  final ApiService api;
  final String csrf, account;
  @override
  State<ReceiveForm> createState() => _ReceiveFormState();
}

class _ReceiveFormState extends State<ReceiveForm> {
  final code = TextEditingController(),
      barcode = TextEditingController(),
      qty = TextEditingController(),
      cost = TextEditingController(),
      note = TextEditingController(),
      expiry = TextEditingController();
  final form = GlobalKey<FormState>();
  final quantityFocus = FocusNode();
  bool confirmed = false, busy = false, scanning = false;
  Map<String, dynamic>? scannedProduct;
  String? receiptSummary;

  Future<void> scan([String? typedBarcode]) async {
    if (busy || scanning || pending != null) return;
    setState(() {
      scanning = true;
      message = '';
      receiptSummary = null;
    });
    var found = false;
    try {
      var value = typedBarcode;
      if (value == null) {
        final result = CameraResult(await scanBarcode().toDart);
        if (!mounted) return;
        if (result.error != null) {
          setState(() => message = result.error!);
          return;
        }
        value = result.value;
      }
      if (value == null || value.isEmpty || value.length > 120) {
        if (typedBarcode != null) {
          setState(() => message = 'กรอกบาร์โค้ด 1–120 ตัวอักษร');
        }
        return;
      }
      final product = Map<String, dynamic>.from(
        await widget.api.get('barcode', {'value': value}) as Map,
      );
      if (!mounted) return;
      setState(() {
        scannedProduct = product;
        barcode.text = value!;
        code.text = product['code'].toString();
        confirmed = false;
      });
      found = true;
    } on ApiFailure catch (error) {
      if (mounted) {
        setState(
          () => message = error.status == 404
              ? 'ไม่พบบาร์โค้ดนี้ในร้าน กรุณาสแกนใหม่หรือกรอกรหัสสินค้า'
              : error.message,
        );
      }
    } catch (_) {
      if (mounted) {
        setState(() => message = 'สแกนไม่สำเร็จ ลองใหม่หรือกรอกรหัสสินค้า');
      }
    } finally {
      if (mounted) {
        setState(() => scanning = false);
        if (found) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted && scannedProduct != null && !busy) {
              quantityFocus.requestFocus();
            }
          });
        }
      }
    }
  }

  String message = '';
  Map<String, dynamic>? pending;
  String get storage => 'kk.mobile.receipt.${widget.account}';
  @override
  void initState() {
    super.initState();
    try {
      final old = web.window.sessionStorage.getItem(storage);
      if (old != null) {
        pending = Map<String, dynamic>.from(jsonDecode(old) as Map);
      }
    } catch (_) {
      message =
          'อ่านรายการค้างไม่สำเร็จ กรุณาตรวจประวัติรับสินค้าก่อนทำรายการใหม่';
    }
  }

  @override
  void dispose() {
    quantityFocus.dispose();
    for (final c in [code, barcode, qty, cost, note, expiry]) {
      c.dispose();
    }
    super.dispose();
  }

  String requestKey() => List.generate(
    24,
    (_) => Random.secure().nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();
  Future<void> send() async {
    if (busy || scanning) return;
    if (pending == null) {
      if (!form.currentState!.validate()) return;
      if (!confirmed) {
        setState(() => message = 'กรุณายืนยันว่าได้รับสินค้าจริงครบตามจำนวน');
        return;
      }
      final data = {
        'requestKey': requestKey(),
        'kind': 'RECEIPT',
        'code': code.text.trim(),
        'quantity': int.parse(qty.text),
        'unitCost': cost.text.trim().isEmpty ? null : cost.text.trim(),
        'note': note.text.trim(),
        'expiryDate': expiry.text.trim().isEmpty ? null : expiry.text.trim(),
      };
      try {
        web.window.sessionStorage.setItem(storage, jsonEncode(data));
        pending = data;
      } catch (_) {
        setState(
          () => message = 'ไม่สามารถเก็บรหัสป้องกันบันทึกซ้ำในเบราว์เซอร์',
        );
        return;
      }
    }
    setState(() {
      busy = true;
      message = '';
      receiptSummary = null;
    });
    try {
      final request = pending!;
      final result = await widget.api.receive(request, widget.csrf) as Map;
      web.window.sessionStorage.removeItem(storage);
      pending = null;
      if (!mounted) return;
      for (final c in [code, barcode, qty, cost, note, expiry]) {
        c.clear();
      }
      setState(() {
        confirmed = false;
        scannedProduct = null;
        receiptSummary =
            '${result['productName'] ?? request['code']} · ${request['quantity']} หน่วย'
            '\nเลขเอกสาร ${result['number'] ?? 'ดูในประวัติรับเข้า'}';
      });
    } catch (error) {
      if (error is ApiFailure && [400, 422].contains(error.status)) {
        web.window.sessionStorage.removeItem(storage);
        pending = null;
      }
      if (mounted) {
        setState(
          () => message = pending == null
              ? '$error'
              : 'ยังไม่ทราบผล: $error · ใช้ปุ่มตรวจรายการเดิม',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Widget field(
    TextEditingController controller,
    String label, {
    String? Function(String?)? validate,
    TextInputType? keyboard,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      controller: controller,
      focusNode: controller == qty ? quantityFocus : null,
      enabled: !busy && !scanning && pending == null,
      onChanged: (_) {
        if (controller == code) {
          setState(() {
            scannedProduct = null;
            confirmed = false;
          });
        }
      },
      keyboardType: keyboard,
      decoration: InputDecoration(labelText: label),
      validator: validate,
    ),
  );
  @override
  Widget build(BuildContext context) => Form(
    key: form,
    child: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('รับสินค้าเข้า', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        const GlassPanel(
          child: ListTile(
            leading: Icon(Icons.inventory_2_rounded, color: Color(0xff2563eb)),
            title: Text('รับเข้านอกใบสั่งซื้อ'),
            subtitle: Text('ตรวจนับสินค้าให้ครบก่อนยืนยัน'),
          ),
        ),
        const SizedBox(height: 14),
        if (receiptSummary case final summary?) ...[
          Semantics(
            liveRegion: true,
            child: GlassPanel(
              child: ListTile(
                leading: const Icon(
                  Icons.check_circle_rounded,
                  color: Color(0xff18734a),
                ),
                title: const Text('บันทึกรับสินค้าแล้ว'),
                subtitle: Text(summary),
              ),
            ),
          ),
          const SizedBox(height: 14),
        ],
        if (pending != null)
          Text('รายการค้าง: ${pending!['code']} × ${pending!['quantity']}'),
        if (pending == null) ...[
          StockActionButton(
            onPressed: busy || scanning ? null : scan,
            icon: Icons.qr_code_scanner,
            label: scanning ? 'กำลังตรวจบาร์โค้ด…' : 'สแกนบาร์โค้ดด้วยกล้อง',
          ),
          const SizedBox(height: 12),
          TextField(
            controller: barcode,
            enabled: !busy && !scanning,
            maxLength: 120,
            textInputAction: TextInputAction.search,
            onChanged: (_) {
              if (scannedProduct == null) return;
              setState(() {
                scannedProduct = null;
                code.clear();
                confirmed = false;
              });
            },
            onSubmitted: (value) => scan(value.trim()),
            decoration: InputDecoration(
              labelText: 'พิมพ์บาร์โค้ด',
              counterText: '',
              suffixIcon: IconButton(
                onPressed: busy || scanning
                    ? null
                    : () => scan(barcode.text.trim()),
                tooltip: 'ค้นหาจากบาร์โค้ด',
                icon: const Icon(Icons.search),
              ),
            ),
          ),
          const SizedBox(height: 12),
          AnimatedSwitcher(
            duration: motionDuration(context),
            child: scannedProduct != null
                ? Card(
                    key: ValueKey(scannedProduct!['code']),
                    color: const Color(0xffedf8f0),
                    child: ListTile(
                      leading: const Icon(
                        Icons.check_circle_rounded,
                        color: Color(0xff18734a),
                      ),
                      title: Text(
                        scannedProduct!['name'].toString(),
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      subtitle: Text(
                        'รหัส ${scannedProduct!['code']} · คงเหลือ ${scannedProduct!['quantity']} หน่วย',
                      ),
                    ),
                  )
                : const SizedBox.shrink(),
          ),
          if (scannedProduct != null) const SizedBox(height: 12),
        ],
        GlassPanel(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'รายละเอียดรับเข้า',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 14),
                field(
                  code,
                  'รหัสสินค้า',
                  validate: (v) =>
                      v == null || v.trim().isEmpty ? 'ระบุรหัสสินค้า' : null,
                ),
                field(
                  qty,
                  'จำนวนรับเข้า',
                  keyboard: TextInputType.number,
                  validate: (v) => (int.tryParse(v ?? '') ?? 0) <= 0
                      ? 'จำนวนต้องมากกว่า 0'
                      : null,
                ),
                field(
                  cost,
                  'ต้นทุนต่อหน่วย (ว่าง = ใช้ต้นทุนเดิม)',
                  keyboard: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  validate: (v) =>
                      v != null &&
                          v.isNotEmpty &&
                          !RegExp(r'^\d+(\.\d{1,4})?$').hasMatch(v)
                      ? 'ต้นทุนใช้ทศนิยมไม่เกิน 4 ตำแหน่ง'
                      : null,
                ),
                field(
                  expiry,
                  'วันหมดอายุ YYYY-MM-DD (ถ้ามี)',
                  validate: (v) =>
                      v != null &&
                          v.isNotEmpty &&
                          (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(v) ||
                              DateTime.tryParse(v) == null)
                      ? 'ระบุวันในรูป YYYY-MM-DD'
                      : null,
                ),
                field(
                  note,
                  'เหตุผลหรือเลขอ้างอิง',
                  validate: (v) => v == null || v.trim().isEmpty
                      ? 'ระบุเหตุผลรับสินค้า'
                      : null,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        GlassPanel(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: confirmed,
              onChanged: busy || pending != null
                  ? null
                  : (v) => setState(() => confirmed = v ?? false),
              title: const Text('ได้รับสินค้าจริงครบตามจำนวนแล้ว'),
            ),
          ),
        ),
        const SizedBox(height: 14),
        StockActionButton(
          onPressed: busy || scanning ? null : send,
          icon: Icons.check_circle_outline_rounded,
          emphasis: true,
          label: busy
              ? 'กำลังตรวจสอบ…'
              : pending == null
              ? 'บันทึกรับสินค้า'
              : 'ตรวจสอบและส่งรายการเดิม',
        ),
        if (message.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 14),
            child: Semantics(liveRegion: true, child: Text(message)),
          ),
      ],
    ),
  );
}
