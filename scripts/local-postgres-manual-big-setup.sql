drop schema if exists datapool_manual_big cascade;
create schema datapool_manual_big;

create table datapool_manual_big.source_1 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual_big.source_2 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual_big.source_3 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual_big.source_4 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual_big.source_5 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual_big.target_pool_big (
    id bigint not null,
    payload text not null,
    source_name text not null
);

insert into datapool_manual_big.source_1(id, payload, source_name)
select gs,
       'payload-db1-' || gs,
       'db1'
from generate_series(1, 100000) gs;

insert into datapool_manual_big.source_2(id, payload, source_name)
select gs,
       'payload-db2-' || gs,
       'db2'
from generate_series(100001, 200000) gs;

insert into datapool_manual_big.source_3(id, payload, source_name)
select gs,
       'payload-db3-' || gs,
       'db3'
from generate_series(200001, 300000) gs;

insert into datapool_manual_big.source_4(id, payload, source_name)
select gs,
       'payload-db4-' || gs,
       'db4'
from generate_series(300001, 400000) gs;

insert into datapool_manual_big.source_5(id, payload, source_name)
select gs,
       'payload-db5-' || gs,
       'db5'
from generate_series(400001, 500000) gs;
