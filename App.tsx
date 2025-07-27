import React, { useState, useEffect } from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  FlatList,
  Alert,
  Platform,
  NativeModules,
  TouchableOpacity,
  Linking,
} from 'react-native';
import DeviceInfo from 'react-native-device-info';

const { WorkProfileModule } = NativeModules;

interface AppInfo {
  appName: string;
  packageName: string;
  versionName: string;
  versionCode: string;
  isWorkProfile?: boolean;
}

type AppFilter = 'all' | 'work' | 'personal';

function App() {
  const [allApps, setAllApps] = useState<AppInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<AppFilter>('all');
  const [hasWorkProfile, setHasWorkProfile] = useState(false);

  useEffect(() => {
    fetchAllApps();
  }, []);

  const getAllAppsFromNative = async (): Promise<AppInfo[]> => {
    try {
      if (!WorkProfileModule) {
        console.log('WorkProfileModule not available, falling back to device-info');
        // Fallback to react-native-device-info
        const installedApps = await DeviceInfo.getInstalledApplications();
        return installedApps.map((app: any) => ({
          appName: app.appName || app.packageName?.split('.').pop() || 'Unknown',
          packageName: app.packageName || 'unknown.package',
          versionName: app.versionName || 'Unknown',
          versionCode: app.versionCode?.toString() || 'Unknown',
          isWorkProfile: false,
        }));
      }

      // Use our native module that properly detects work vs personal apps
      const allApps = await WorkProfileModule.getAllApps();
      console.log('Raw apps from native module:', allApps.length);

      const formattedApps = allApps.map((app: any) => ({
        appName: app.appName || app.packageName?.split('.').pop() || 'Unknown',
        packageName: app.packageName || 'unknown.package',
        versionName: app.versionName || 'Unknown',
        versionCode: app.versionCode || 'Unknown',
        isWorkProfile: app.isWorkProfile === true, // Ensure boolean
      }));

      console.log('Formatted apps:', formattedApps.length);
      const workApps = formattedApps.filter(app => app.isWorkProfile);
      const personalApps = formattedApps.filter(app => !app.isWorkProfile);
      console.log(`Work apps: ${workApps.length}, Personal apps: ${personalApps.length}`);
      
      // Show sample of each type
      console.log('Sample work apps:', workApps.slice(0, 3).map(app => ({
        name: app.appName,
        package: app.packageName,
        isWork: app.isWorkProfile
      })));
      console.log('Sample personal apps:', personalApps.slice(0, 3).map(app => ({
        name: app.appName,
        package: app.packageName,
        isWork: app.isWorkProfile
      })));

      return formattedApps;
    } catch (error) {
      console.error('Error getting all apps:', error);
      return [];
    }
  };

  const fetchAllApps = async () => {
    try {
      if (Platform.OS !== 'android') {
        Alert.alert(
          'Not Supported',
          'This feature is only available on Android',
        );
        setLoading(false);
        return;
      }

      // Check if device has work profile
      let deviceHasWorkProfile = false;
      if (WorkProfileModule) {
        try {
          deviceHasWorkProfile = await WorkProfileModule.hasWorkProfile();
          setHasWorkProfile(deviceHasWorkProfile);
          console.log('Device has work profile:', deviceHasWorkProfile);
        } catch (error) {
          console.log('Could not check work profile status:', error);
        }
      }

      // Get all apps (both work and personal) with correct flags
      const allAppsData = await getAllAppsFromNative();

      // Sort by app name
      allAppsData.sort((a, b) => a.appName.localeCompare(b.appName));

      setAllApps(allAppsData);
      console.log('Total apps found:', allAppsData.length);
      console.log(
        'Work apps:',
        allAppsData.filter(app => app.isWorkProfile).length,
      );
      console.log(
        'Regular apps:',
        allAppsData.filter(app => !app.isWorkProfile).length,
      );
    } catch (error: any) {
      console.error('Error fetching apps:', error);
      Alert.alert(
        'Error',
        `Failed to fetch apps: ${error.message || 'Unknown error'}`,
      );
    } finally {
      setLoading(false);
    }
  };

  // Filter apps based on current filter
  const filteredApps = allApps.filter(app => {
    switch (filter) {
      case 'work':
        return app.isWorkProfile === true;
      case 'personal':
        return app.isWorkProfile === false;
      default:
        return true;
    }
  });

  // Debug logging
  console.log(
    `Filter: ${filter}, Total apps: ${allApps.length}, Filtered apps: ${filteredApps.length}`,
  );
  if (filter === 'work') {
    console.log(
      'Work apps in filtered list:',
      filteredApps.slice(0, 5).map(app => ({
        name: app.appName,
        package: app.packageName,
        isWork: app.isWorkProfile,
      })),
    );
  }

  const renderFilterButton = (filterType: AppFilter, label: string) => (
    <TouchableOpacity
      style={[
        styles.filterButton,
        filter === filterType && styles.filterButtonActive,
      ]}
      onPress={() => setFilter(filterType)}
    >
      <Text
        style={[
          styles.filterButtonText,
          filter === filterType && styles.filterButtonTextActive,
        ]}
      >
        {label}
      </Text>
    </TouchableOpacity>
  );

  const openApp = async (packageName: string, appName: string, isWorkProfile: boolean) => {
    try {
      if (!WorkProfileModule) {
        Alert.alert('Error', 'Cannot launch apps - native module not available');
        return;
      }

      // Use native module to launch the app with work profile flag
      await WorkProfileModule.launchApp(packageName, isWorkProfile);
      console.log(`Successfully launched ${appName} (${packageName}) - Work Profile: ${isWorkProfile}`);
    } catch (error: any) {
      console.error('Error opening app:', error);
      
      if (error.code === 'APP_NOT_FOUND') {
        // App cannot be launched, offer to open in Play Store
        Alert.alert(
          'App Not Available',
          `Cannot open ${appName}. Would you like to view it in the Play Store?`,
          [
            { text: 'Cancel', style: 'cancel' },
            { 
              text: 'Open Play Store', 
              onPress: () => {
                const playStoreUrl = `market://details?id=${packageName}`;
                Linking.openURL(playStoreUrl).catch(() => {
                  // Fallback to web URL if Play Store app not available
                  Linking.openURL(`https://play.google.com/store/apps/details?id=${packageName}`);
                });
              }
            }
          ]
        );
      } else {
        Alert.alert('Error', `Failed to open ${appName}: ${error.message || 'Unknown error'}`);
      }
    }
  };

  const renderAppItem = ({ item }: { item: AppInfo }) => (
    <TouchableOpacity 
      style={[styles.appItem, item.isWorkProfile && styles.workAppItem]}
      onPress={() => openApp(item.packageName, item.appName, item.isWorkProfile)}
      activeOpacity={0.7}
    >
      <View style={styles.appHeader}>
        <Text style={styles.appName}>{item.appName}</Text>
        {item.isWorkProfile && (
          <View style={styles.workBadge}>
            <Text style={styles.workBadgeText}>Work</Text>
          </View>
        )}
      </View>
      <Text style={styles.packageName}>{item.packageName}</Text>
      <Text style={styles.versionInfo}>
        Version: {item.versionName} ({item.versionCode})
      </Text>
      <Text style={styles.tapHint}>Tap to open</Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.title}>Apps Manager</Text>
        <Text style={styles.subtitle}>
          {loading ? 'Loading...' : `${filteredApps.length} apps`}
          {hasWorkProfile &&
            !loading &&
            ` (${allApps.filter(app => app.isWorkProfile).length} work, ${
              allApps.filter(app => !app.isWorkProfile).length
            } personal)`}
        </Text>
      </View>

      {!loading && (
        <View style={styles.filterContainer}>
          {renderFilterButton('all', 'All Apps')}
          {renderFilterButton('personal', 'Personal')}
          {hasWorkProfile && renderFilterButton('work', 'Work')}
        </View>
      )}

      {!loading && filteredApps.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyText}>
            No {filter === 'all' ? '' : filter} apps found
          </Text>
        </View>
      ) : (
        <FlatList
          data={filteredApps}
          renderItem={renderAppItem}
          keyExtractor={(item, index) =>
            `${item.packageName}-${
              item.isWorkProfile ? 'work' : 'personal'
            }-${index}`
          }
          style={styles.list}
          contentContainerStyle={styles.listContent}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    padding: 20,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginTop: 5,
  },
  filterContainer: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  filterButton: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    marginRight: 10,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
    borderWidth: 1,
    borderColor: '#ddd',
  },
  filterButtonActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  filterButtonText: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
  },
  filterButtonTextActive: {
    color: '#fff',
  },
  list: {
    flex: 1,
  },
  listContent: {
    padding: 15,
  },
  appItem: {
    backgroundColor: '#fff',
    padding: 15,
    marginBottom: 10,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 1,
    },
    shadowOpacity: 0.22,
    shadowRadius: 2.22,
    elevation: 3,
  },
  workAppItem: {
    borderLeftWidth: 4,
    borderLeftColor: '#FF9500',
  },
  appHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 5,
  },
  appName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    flex: 1,
  },
  workBadge: {
    backgroundColor: '#FF9500',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  workBadgeText: {
    fontSize: 10,
    color: '#fff',
    fontWeight: 'bold',
  },
  packageName: {
    fontSize: 14,
    color: '#666',
    marginBottom: 5,
  },
  versionInfo: {
    fontSize: 12,
    color: '#999',
    marginBottom: 5,
  },
  tapHint: {
    fontSize: 11,
    color: '#007AFF',
    fontStyle: 'italic',
    textAlign: 'center',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#666',
  },
});

export default App;
