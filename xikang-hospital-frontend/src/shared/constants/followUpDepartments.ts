/** 内分泌科 department.id，与 init.sql / 种子数据一致 */
export const ENDOCRINE_DEPARTMENT_ID = 7

export function isEndocrineDepartment(departmentId?: number | null): boolean {
  return departmentId === ENDOCRINE_DEPARTMENT_ID
}
