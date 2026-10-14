<!--
  根组件

  仅包含 <router-view />，所有布局和页面内容由路由控制。
  组员A 负责维护此文件。

  设计说明：
  - 不在 App.vue 中放置任何布局代码（如侧边栏、顶栏），
    因为 B端和 C端使用不同的布局组件（BLayout / CLayout），
    由路由根据路径自动选择对应的布局。
  - 这种设计使布局与路由解耦，新增页面时只需在 routes.js 中添加路由，
    无需修改 App.vue。
-->

<template>
  <router-view />
</template>

<script setup>
// 根组件无需额外逻辑
</script>
