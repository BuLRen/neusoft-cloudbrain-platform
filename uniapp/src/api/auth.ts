import { request } from './request'
import type { PatientInfo } from '../stores/session'

export interface CaptchaResult { captchaId:string; imageBase64:string }
export interface LoginResult { userId:string; username:string; role:string; token:string; refreshToken:string; realName:string; patients:PatientInfo[] }

export const authApi = {
  captcha: () => request<CaptchaResult>({ url:'/auth/captcha', skipAuth:true, showError:false }),
  login: (data:{username:string;password:string;captchaId:string;captchaCode:string}) => request<LoginResult>({ url:'/auth/login', method:'POST', data, skipAuth:true }),
  me: () => request<LoginResult>({ url:'/auth/me', showError:false }),
  logout: () => request<void>({ url:'/auth/logout', method:'POST', showError:false }),
}
