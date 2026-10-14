/**
 * 常量定义
 * 
 * 集中管理项目中使用的枚举值和常量，避免魔法字符串散落在各组件中。
 * 组员A 和 组员C 共同维护此文件。
 */

/** 用户角色枚举 */
export const ROLE = {
  ATTENDEE: 'attendee',   // 参会者
  ORGANIZER: 'organizer'  // 举办方
}

/** 活动状态枚举 */
export const EVENT_STATUS = {
  DRAFT: 'draft',     // 草稿
  ONLINE: 'online',   // 上线
  OFFLINE: 'offline'  // 下线
}

/** 门票状态枚举 */
export const TICKET_STATUS = {
  PENDING: 'pending',   // 待签到
  CHECKED: 'checked',   // 已签到
  INVALID: 'invalid',   // 已失效
  REFUNDED: 'refunded'  // 已退票（预留）
}

/** 性别枚举 */
export const GENDER = {
  MALE: 'male',
  FEMALE: 'female'
}

/** 活动列表范围 */
export const EVENT_SCOPE = {
  PUBLIC: 'public',       // 公开列表（仅 online）
  ORGANIZER: 'organizer'  // 举办方自己的所有活动
}

/** 业务码枚举（与后端约定一致） */
export const BIZ_CODE = {
  SUCCESS: 200,
  PARAM_ERROR: 4000,
  AUTH_FAILED: 4001,
  PERMISSION_DENIED: 4002,
  NOT_FOUND: 4003,
  CONFLICT: 4004,
  BIZ_ERROR: 4100
}

/** localStorage 键名常量 */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER_ROLE: 'userRole',
  USER_INFO: 'userInfo'
}
