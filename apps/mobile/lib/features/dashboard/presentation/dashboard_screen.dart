import 'package:flutter/material.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/tabs/create_route_tab.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/tabs/discover_routes_tab.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/tabs/incoming_requests_tab.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/tabs/outgoing_requests_tab.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/tabs/profile_tab.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 5,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Route Mates'),
          actions: [
            IconButton(
              onPressed: () {
                controller.logout();
              },
              icon: const Icon(Icons.logout),
              tooltip: 'Logout',
            ),
          ],
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Profile'),
              Tab(text: 'Create Route'),
              Tab(text: 'Discover'),
              Tab(text: 'Outgoing'),
              Tab(text: 'Incoming'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            ProfileTab(controller: controller),
            CreateRouteTab(controller: controller),
            DiscoverRoutesTab(controller: controller),
            OutgoingRequestsTab(controller: controller),
            IncomingRequestsTab(controller: controller),
          ],
        ),
      ),
    );
  }
}
