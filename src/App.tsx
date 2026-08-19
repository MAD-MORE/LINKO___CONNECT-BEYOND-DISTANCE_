import React from 'react';
import {SafeAreaView, StyleSheet, Text, View} from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>LINKO</Text>
        <Text style={styles.subtitle}>Connect Beyond Distance</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#050912'},
  content: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  title: {color: '#eaf2ff', fontSize: 42, fontWeight: '800', letterSpacing: 3},
  subtitle: {marginTop: 8, color: '#7fe3ff', fontSize: 14},
});
