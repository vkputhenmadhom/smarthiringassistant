import React from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useQuery, useSubscription } from '@apollo/client';
import { Layout as AntLayout, Menu, Avatar, Dropdown, Badge, Button, Drawer, Grid } from 'antd';
import {
  DashboardOutlined, SearchOutlined, FileTextOutlined,
  UserOutlined, LogoutOutlined, ReadOutlined, BellOutlined, MenuOutlined
} from '@ant-design/icons';
import { logout } from '../../store/authSlice';
import { RootState } from '../../store';
import { MY_NOTIFICATIONS_QUERY, NEW_NOTIFICATION_SUBSCRIPTION, Notification } from '../../graphql';

const { Header, Sider, Content } = AntLayout;

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/jobs',      icon: <SearchOutlined />,    label: 'Find Jobs' },
  { key: '/resume',    icon: <FileTextOutlined />,  label: 'My Resume' },
  { key: '/interview', icon: <ReadOutlined />,      label: 'Interview Prep' },
];

const Layout: React.FC = () => {
  const { useBreakpoint } = Grid;
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  const navigate  = useNavigate();
  const dispatch  = useDispatch();
  const user      = useSelector((s: RootState) => s.auth.user);
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(false);
  const [liveNotifications, setLiveNotifications] = React.useState<Notification[]>([]);

  React.useEffect(() => {
    setLiveNotifications([]);
  }, [user?.id]);

  const { data: notificationData } = useQuery<{ myNotifications: Notification[] }>(MY_NOTIFICATIONS_QUERY, {
    variables: { unreadOnly: true },
    skip: !user?.id,
    fetchPolicy: 'cache-and-network',
  });

  useSubscription<{ newNotification: Notification }>(NEW_NOTIFICATION_SUBSCRIPTION, {
    variables: { userId: user?.id ?? '' },
    skip: !user?.id,
    onData: ({ data }) => {
      const notification = data.data?.newNotification;
      if (!notification) {
        return;
      }
      setLiveNotifications(previous =>
        previous.some(item => item.id === notification.id)
          ? previous
          : [notification, ...previous]
      );
    },
  });

  const unreadNotificationCount = React.useMemo(() => {
    const notifications = [
      ...(notificationData?.myNotifications ?? []),
      ...liveNotifications,
    ].filter(notification => !notification.read);

    return new Set(notifications.map(notification => notification.id)).size;
  }, [notificationData?.myNotifications, liveNotifications]);

  const userMenu = {
    items: [
      { key: 'profile', icon: <UserOutlined />,  label: 'Profile' },
      { key: 'logout',  icon: <LogoutOutlined />, label: 'Sign Out',
        onClick: () => { dispatch(logout()); navigate('/login'); } },
    ],
  };

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider
          width={220}
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          style={{ background: '#001529' }}
        >
          <div style={{ padding: '20px 16px', color: '#fff' }}>
            <h2 style={{ color: '#fff', margin: 0, fontSize: collapsed ? '.95rem' : '1.1rem' }}>SHA Candidate</h2>
            {!collapsed && <p style={{ color: '#aaa', fontSize: '.75rem', margin: '4px 0 0' }}>Career Portal</p>}
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[window.location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
      )}

      <Drawer
        title="SHA Candidate"
        placement="left"
        width={260}
        onClose={() => setDrawerOpen(false)}
        open={isMobile && drawerOpen}
        styles={{ body: { padding: 0 } }}
      >
        <Menu
          mode="inline"
          selectedKeys={[window.location.pathname]}
          items={menuItems}
          onClick={({ key }) => {
            navigate(key);
            setDrawerOpen(false);
          }}
        />
      </Drawer>

      <AntLayout>
        <Header style={{ background: '#fff', display: 'flex', alignItems: 'center',
                         justifyContent: 'flex-end', gap: 16, padding: '0 16px',
                         boxShadow: '0 2px 8px rgba(0,0,0,.08)' }}>
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined style={{ fontSize: 18 }} />}
              onClick={() => setDrawerOpen(true)}
            />
          )}
          <Badge count={unreadNotificationCount} size="small">
            <Button type="text" icon={<BellOutlined style={{ fontSize: 18 }} />} />
          </Badge>
          <Dropdown menu={userMenu} placement="bottomRight">
            <Avatar style={{ cursor: 'pointer', background: '#3f51b5' }}>
              {user?.username?.[0]?.toUpperCase() ?? 'U'}
            </Avatar>
          </Dropdown>
        </Header>
        <Content style={{ margin: isMobile ? 12 : 24, background: '#f5f6fa' }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  );
};

export default Layout;
